# Embedded report font

- Source: Google Fonts `ofl/notosanssc/NotoSansSC[wght].ttf` at https://github.com/google/fonts/tree/main/ofl/notosanssc
- License: SIL Open Font License 1.1, retained in `OFL.txt`. The reserved name in that notice is `Source`; the bundled font's primary name is Noto Sans SC.
- This static instance was generated with fontTools 4.66.1 at `wght=400` for PDFBox TrueType embedding. No Windows font paths are used.
- Source SHA-256: `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`
- Static SHA-256: `ab9c9ce8e6fb7eb86d95d5241d7b4b7182fd54e311c936e2e8d0752ed710117d`

Reproduction (using an explicitly downloaded copy of the source and a temporary fontTools environment):

```text
python -m fontTools.varLib.instancer NotoSansSC-variable.ttf wght=400 --output NotoSansSC-Regular.ttf
```

The font is not sold separately. Its license continues to apply to this modified instance; generated report documents are not relicensed by the font license.
