# PaperCript

Android app that stores your passwords **on paper, encrypted**.

You encrypt a password with your master password, the app turns it into a card with a QR
code, and you print an A4 sheet of cut-out cards. To get a password back, you scan its QR
and type the master password.

The point is that **losing the paper costs you nothing**: a card is worthless without the
master password. Keep them in a drawer, split them up, or leave a copy at a friend's place.

The app stores nothing — no passwords, no master password, no history. The paper is the
only storage.

---

## Using it

**Encrypt** → label, password (typed or generated) and master
password → *Add to sheet and next*. The master password is kept between entries so you can
chain several in one go.

**Sheet** → pick columns and rows, see how the page will look, then *Print or save as PDF*.
The Android print dialog handles both a real printer and a PDF. Then cut them out.

**Decrypt** → *Scan the card QR* → master password. The label travels inside the QR, so the
master password is all you type.

> ⚠️ The pending card list only lives in memory while the app is open. Print before closing.

> ⚠️ The master password has no recovery. Forget it and every printed card turns into
> recycling, all at once.

## The sheet

You choose the layout (1–6 columns × 1–8 rows) and the app tells you the real size in
millimetres of both the card and the QR. Fewer cards per sheet means a bigger QR and an
easier scan. Below roughly 18 mm of QR it warns you, because that is where each module drops
under 0.45 mm and phone cameras start to struggle.

Each card carries the label, the QR, and the code in small print. That printed text is the
rescue path if a QR is ever damaged: you type it back in the app with a 32-key keypad that
only offers valid characters.

## Code format

```
[ version 1B ][ salt 4B ][ ciphertext NB ][ tag 5B ]
```

All of it in Crockford-style Base32, an alphabet **without I, L, O or U** so they cannot be
confused with 1, 1, 0 and V when typing.

- **Key derivation**: `scrypt(master_password, domain + version + salt)` → 64 bytes, split
  into an encryption key and a MAC key. v3 uses N=65536 (64 MB), roughly a second per
  operation on a current phone.
- **Encryption**: AES-256-CTR with a fixed IV. This is safe because the salt is random per
  encryption, so the key never repeats and no keystream is ever reused.
- **Integrity**: HMAC-SHA256 truncated to 40 bits, encrypt-then-MAC. It catches a mistyped
  character and a wrong master password at the same time — which is why a failure cannot
  tell you which of the two it was.
- **The label is not part of the derivation.** It is your own note: change it or misspell it
  without breaking the card.
- **The version byte selects the parameters**, so raising the cost later does not invalidate
  what is already printed.

The QR holds `LBR1|code|label` with error correction level Q (survives up to 25% damage),
because these cards get folded and handled.

> ⚠️ **The domain string and parameters are frozen** from the first card you print. The key
> is never stored: it is recomputed in full on every decryption. Changing any ingredient
> makes already-issued cards unreadable forever.

## Security

**Already post-quantum**, with no effort: everything here is symmetric (scrypt, AES-256,
HMAC-SHA256). Shor's algorithm breaks RSA and elliptic curves, neither of which appears
anywhere in this app; Grover only cuts AES-256 down to about 128 effective bits, far out of
reach. The real weak link is the entropy of your master password — a 6 or 7 word random
passphrase (77–90 bits) is what actually moves the needle.

**What the app does:**

- **No network permission.** The APK declares only `CAMERA`, and `INTERNET` is explicitly
  stripped with `tools:node="remove"`. Without it the kernel will not let the app open a
  socket — that is not a promise, it is an inability.
- **Fully open source.** QR codes are read with [ZXing](https://github.com/zxing/zxing);
  there are no Google Play Services or other proprietary libraries.
- `FLAG_SECURE`: no screenshots, no recents thumbnail, no screen recording.
- The clipboard is flagged as sensitive and cleared 45 s after copying **or when you return
  to the app**, whichever happens while it is in the foreground. There is also a button to
  clear it immediately.
- No backups (`allowBackup=false`) and nothing written to disk.
- Derived keys are zeroed in memory as soon as they are used.

**What it does not cover, and you should know:**

- A malicious **accessibility service** can read what is on screen. `FLAG_SECURE` does not
  stop it. This is the realest hole.
- Your **keyboard** sees everything you type, including the master password. Inherent to
  Android.
- **No app can clear the clipboard from the background.** Since Android 10 the system denies
  access to anything without focus (`Denying clipboard access ... application is not in
  focus`), so a timer that fires while you are pasting elsewhere clears nothing. Hence the
  clear-on-return and the manual button. If you never come back to the app, the password
  stays there until the system cleans it up.
- The code length reveals the password length.

**None of this has been externally audited.** It is standard cryptography assembled with
care, but run the full cycle with a throwaway password before trusting it with a real one.

## Languages

Eleven languages, all 78 strings complete in every one: **English** (default), **Spanish,
Catalan, Galician, Basque, French, German, Italian, Portuguese, Simplified Chinese and
Japanese**.

The app follows the phone language and falls back to English for anything else. On
Android 13+ it declares `localeConfig`, so you can pin a language for PaperCript alone in
**Settings → Apps → PaperCript → Language**, without touching the system.

> The translations have not been reviewed by native speakers. If something reads wrong, it
> is a text file — fixing it touches no code.

**To add a language**, copy `res/values/strings.xml` to `res/values-xx/`, translate it, and
add `<locale android:name="xx" />` to `res/xml/locales_config.xml`. No Kotlin involved.

Then check that the new language lines up:

```
pwsh tools/check-locales.ps1
```

It verifies that no key is missing or extra and that the format specifiers (`%1$d`,
`%2$s`…) match the English ones. That last part matters more than it looks: a `%1$s`
translated as `%1$d` compiles happily and blows up while formatting, in front of the user.

For Chinese and Japanese, **each string must sit on a single line**. Android joins wrapped
lines with a space, which in languages without word separators lands in the middle of a
sentence.

## Building

```
./gradlew testDebugUnitTest assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. For real use, build a signed release with
your own keystore.

Requires JDK 17+ and Android SDK 35. No runtime network dependencies.

## License

[MIT](LICENSE) © [javimentallab](https://github.com/javimentallab)
