#!/usr/bin/env python3
"""Uch ilova — uch qiyofa (v5.0). Ranglar (yorug'/qorong'i), mavzu va komponent uslublari.

- Agent  (app/src/agent)  — oddiy, do'stona, yorug': yashil-zangori brend, yumaloq burchaklar, katta tugmalar.
- Admin  (app/src/admin)  — "dasturchi quroli": har doim qorong'i terminal fon, yashil-amber urg'u,
                            monospace raqamlar, o'tkir burchaklar.
- Mijoz  (mijoz)          — uchinchi qiyofa: indigo-siyoh rang, juda yumaloq kartalar.

Resurs nomlari uchta ilovada BIR XIL (brand_*, Theme.Dokon/Theme.Mijoz, Widget.App.*), faqat qiymatlari farq qiladi —
shuning uchun maketlar va kod o'zgarmaydi.

Ishlatish: python3 tools/gen_theme.py
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Umumiy ma'noli ranglar (qarz/muvaffaqiyat/ogohlantirish) — har palitra ustiga qo'shiladi
def sem(debt, success, succ_c, on_succ_c, warn_c, on_warn_c):
    return {
        'debt': debt, 'success': success, 'success_container': succ_c, 'on_success_container': on_succ_c,
        'warning_container': warn_c, 'on_warning_container': on_warn_c,
    }


# rol: (yorug', qorong'i)
AGENT = {
    'primary': ('#006A60', '#53DBCA'),
    'on_primary': ('#FFFFFF', '#003732'),
    'primary_container': ('#B3EEE4', '#005048'),
    'on_primary_container': ('#00201C', '#74F8E6'),
    'secondary': ('#4A635F', '#B1CCC6'),
    'on_secondary': ('#FFFFFF', '#1C3531'),
    'secondary_container': ('#CCE8E2', '#334B47'),
    'on_secondary_container': ('#05201C', '#CCE8E2'),
    'tertiary': ('#7B5800', '#F0BF61'),
    'on_tertiary': ('#FFFFFF', '#412D00'),
    'tertiary_container': ('#FFDEA6', '#5D4200'),
    'on_tertiary_container': ('#271900', '#FFDEA6'),
    'error': ('#BA1A1A', '#FFB4AB'),
    'on_error': ('#FFFFFF', '#690005'),
    'error_container': ('#FFDAD6', '#93000A'),
    'on_error_container': ('#410002', '#FFDAD6'),
    'surface': ('#F7FAF8', '#101413'),
    'on_surface': ('#191C1B', '#E0E3E1'),
    'surface_variant': ('#DAE5E2', '#3F4947'),
    'on_surface_variant': ('#3F4947', '#BEC9C6'),
    'outline': ('#6F7977', '#899390'),
    'outline_variant': ('#BEC9C6', '#3F4947'),
    'inverse_surface': ('#2D3130', '#E0E3E1'),
    'inverse_on_surface': ('#EFF1EF', '#2D3130'),
    'inverse_primary': ('#53DBCA', '#006A60'),
    'surface_dim': ('#D8DBD9', '#101413'),
    'surface_bright': ('#F7FAF8', '#363A39'),
    'surface_container_lowest': ('#FFFFFF', '#0B0F0E'),
    'surface_container_low': ('#F2F4F2', '#191C1B'),
    'surface_container': ('#ECEEEC', '#1D201F'),
    'surface_container_high': ('#E6E9E7', '#272B2A'),
    'surface_container_highest': ('#E0E3E1', '#323534'),
}
AGENT.update(sem(('#BA1A1A', '#FFB4AB'), ('#256C27', '#8ED885'), ('#C9F2C0', '#0B3D10'), ('#002203', '#C9F2C0'),
                 ('#FFDEA6', '#5D4200'), ('#271900', '#FFDEA6')))

# Admin: yorug' rejim ham QORONG'I (terminal). Ikki ustun ham bir xil.
def d(c):
    return (c, c)


ADMIN = {
    'primary': d('#2BD67B'),
    'on_primary': d('#00160A'),
    'primary_container': d('#00512C'),
    'on_primary_container': d('#7FF8B0'),
    'secondary': d('#64D8CB'),
    'on_secondary': d('#00201C'),
    'secondary_container': d('#004A43'),
    'on_secondary_container': d('#9EF2E6'),
    'tertiary': d('#FFC14D'),
    'on_tertiary': d('#2A1D00'),
    'tertiary_container': d('#4A3600'),
    'on_tertiary_container': d('#FFDFA0'),
    'error': d('#FF8A80'),
    'on_error': d('#3A0003'),
    'error_container': d('#6B0007'),
    'on_error_container': d('#FFDAD6'),
    'surface': d('#070B09'),
    'on_surface': d('#D7E3DB'),
    'surface_variant': d('#2A322D'),
    'on_surface_variant': d('#A9B8AE'),
    'outline': d('#6E7D74'),
    'outline_variant': d('#2A322D'),
    'inverse_surface': d('#D7E3DB'),
    'inverse_on_surface': d('#1A211C'),
    'inverse_primary': d('#00693B'),
    'surface_dim': d('#070B09'),
    'surface_bright': d('#2A322D'),
    'surface_container_lowest': d('#040706'),
    'surface_container_low': d('#0C110E'),
    'surface_container': d('#111713'),
    'surface_container_high': d('#171E19'),
    'surface_container_highest': d('#1E2620'),
}
ADMIN.update(sem(d('#FF8A80'), d('#2BD67B'), d('#00341C'), d('#7FF8B0'), d('#4A3600'), d('#FFDFA0')))

# Mijoz: indigo-siyoh
MIJOZ = {
    'primary': ('#4B4AC4', '#C2C1FF'),
    'on_primary': ('#FFFFFF', '#1A1A8E'),
    'primary_container': ('#E2DFFF', '#3331A9'),
    'on_primary_container': ('#0C0664', '#E2DFFF'),
    'secondary': ('#5D5C72', '#C6C4DD'),
    'on_secondary': ('#FFFFFF', '#2F2E42'),
    'secondary_container': ('#E3DFF9', '#454459'),
    'on_secondary_container': ('#1A1A2C', '#E3DFF9'),
    'tertiary': ('#79536A', '#E9B9D3'),
    'on_tertiary': ('#FFFFFF', '#46263A'),
    'tertiary_container': ('#FFD8EC', '#5F3C51'),
    'on_tertiary_container': ('#2E1125', '#FFD8EC'),
    'error': ('#BA1A1A', '#FFB4AB'),
    'on_error': ('#FFFFFF', '#690005'),
    'error_container': ('#FFDAD6', '#93000A'),
    'on_error_container': ('#410002', '#FFDAD6'),
    'surface': ('#FCF8FF', '#131318'),
    'on_surface': ('#1B1B21', '#E4E1E9'),
    'surface_variant': ('#E4E1EC', '#47464F'),
    'on_surface_variant': ('#47464F', '#C8C5D0'),
    'outline': ('#78767F', '#928F9A'),
    'outline_variant': ('#C8C5D0', '#47464F'),
    'inverse_surface': ('#303036', '#E4E1E9'),
    'inverse_on_surface': ('#F3EFF7', '#303036'),
    'inverse_primary': ('#C2C1FF', '#4B4AC4'),
    'surface_dim': ('#DCD9E0', '#131318'),
    'surface_bright': ('#FCF8FF', '#39383F'),
    'surface_container_lowest': ('#FFFFFF', '#0E0E13'),
    'surface_container_low': ('#F6F2FA', '#1B1B21'),
    'surface_container': ('#F0ECF4', '#1F1F25'),
    'surface_container_high': ('#EAE7EF', '#2A292F'),
    'surface_container_highest': ('#E4E1E9', '#35343A'),
}
MIJOZ.update(sem(('#BA1A1A', '#FFB4AB'), ('#256C27', '#8ED885'), ('#C9F2C0', '#0B3D10'), ('#002203', '#C9F2C0'),
                 ('#FFDEA6', '#5D4200'), ('#271900', '#FFDEA6')))

ATTRS = [
    ('colorPrimary', 'primary'), ('colorOnPrimary', 'on_primary'), ('colorPrimaryContainer', 'primary_container'),
    ('colorOnPrimaryContainer', 'on_primary_container'), ('colorPrimaryInverse', 'inverse_primary'),
    ('colorSecondary', 'secondary'), ('colorOnSecondary', 'on_secondary'), ('colorSecondaryContainer', 'secondary_container'),
    ('colorOnSecondaryContainer', 'on_secondary_container'),
    ('colorTertiary', 'tertiary'), ('colorOnTertiary', 'on_tertiary'), ('colorTertiaryContainer', 'tertiary_container'),
    ('colorOnTertiaryContainer', 'on_tertiary_container'),
    ('colorError', 'error'), ('colorOnError', 'on_error'), ('colorErrorContainer', 'error_container'), ('colorOnErrorContainer', 'on_error_container'),
    ('colorSurface', 'surface'), ('colorOnSurface', 'on_surface'), ('colorSurfaceVariant', 'surface_variant'),
    ('colorOnSurfaceVariant', 'on_surface_variant'), ('colorSurfaceInverse', 'inverse_surface'), ('colorOnSurfaceInverse', 'inverse_on_surface'),
    ('colorOutline', 'outline'), ('colorOutlineVariant', 'outline_variant'),
    ('colorSurfaceDim', 'surface_dim'), ('colorSurfaceBright', 'surface_bright'),
    ('colorSurfaceContainerLowest', 'surface_container_lowest'), ('colorSurfaceContainerLow', 'surface_container_low'),
    ('colorSurfaceContainer', 'surface_container'), ('colorSurfaceContainerHigh', 'surface_container_high'),
    ('colorSurfaceContainerHighest', 'surface_container_highest'),
    ('colorOnBackground', 'on_surface'),
]

HDR = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- tools/gen_theme.py yaratgan — qo'lda o'zgartirmang -->"]


def colors_xml(palette, idx, extra, launcher_bg=None):
    out = HDR + ['<resources>']
    if launcher_bg:
        out.append(f'    <color name="ic_launcher_background">{launcher_bg}</color>')
    for k, v in palette.items():
        out.append(f'    <color name="brand_{k}">{v[idx]}</color>')
    for k, v in extra.items():
        out.append(f'    <color name="{k}">{v[idx]}</color>')
    out.append('</resources>')
    return '\n'.join(out) + '\n'


def theme_xml(name, light_bars):
    """[light_bars] — status/navigation panel belgilar qora bo'lsinmi (yorug' fon uchun true)."""
    o = HDR + ['<resources xmlns:tools="http://schemas.android.com/tools">',
               f'    <style name="{name}" parent="Theme.Material3.DayNight.NoActionBar">']
    for a, c in ATTRS:
        o.append(f'        <item name="{a}">@color/brand_{c}</item>')
    o += [
        '        <item name="android:colorBackground">@color/brand_surface</item>',
        '        <item name="android:windowBackground">@color/brand_surface</item>',
        '        <item name="android:statusBarColor">@color/brand_surface</item>',
        '        <item name="android:navigationBarColor">@color/brand_surface_container</item>',
        f'        <item name="android:windowLightStatusBar">{"true" if light_bars else "false"}</item>',
        f'        <item name="android:windowLightNavigationBar" tools:targetApi="27">{"true" if light_bars else "false"}</item>',
        '        <item name="textAppearanceBodySmall">@style/TextAppearance.App.BodySmall</item>',
        '        <item name="textAppearanceLabelSmall">@style/TextAppearance.App.LabelSmall</item>',
        '        <item name="textAppearanceLabelMedium">@style/TextAppearance.App.LabelMedium</item>',
        '        <item name="textAppearanceTitleLarge">@style/TextAppearance.App.TitleLarge</item>',
        '        <item name="toolbarStyle">@style/Widget.App.Toolbar</item>',
        '        <item name="bottomNavigationStyle">@style/Widget.App.BottomNavigation</item>',
        '        <item name="materialButtonStyle">@style/Widget.App.Button</item>',
        '        <item name="materialButtonOutlinedStyle">@style/Widget.App.Button.Outlined</item>',
        '        <item name="chipStyle">@style/Widget.App.Chip</item>',
        '        <item name="materialSwitchStyle">@style/Widget.App.Switch</item>',
        '        <item name="snackbarStyle">@style/Widget.Material3.Snackbar</item>',
        '    </style>',
        '</resources>',
    ]
    return '\n'.join(o) + '\n'


def styles_xml(look):
    """[look]: radius, font (sarlavha/summa shrifti), num (raqam shrifti), card_bg, btn_radius, nav_labels."""
    r = look['radius']
    f = look['font']
    num = look['num']
    def fam(v):
        return f'        <item name="android:fontFamily">{v}</item>\n' if v else ''
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- tools/gen_theme.py yaratgan — qo'lda o'zgartirmang. {look['note']} -->
<resources>
    <!-- Matn -->
    <style name="TextAppearance.App.BodySmall" parent="TextAppearance.Material3.BodySmall">
        <item name="android:textSize">13sp</item>
        <item name="lineHeight">18sp</item>
    </style>
    <style name="TextAppearance.App.LabelSmall" parent="TextAppearance.Material3.LabelSmall">
        <item name="android:textSize">12sp</item>
{fam(f)}    </style>
    <style name="TextAppearance.App.LabelMedium" parent="TextAppearance.Material3.LabelMedium">
        <item name="android:textSize">13sp</item>
{fam(f)}    </style>
    <style name="TextAppearance.App.TitleLarge" parent="TextAppearance.Material3.TitleLarge">
        <item name="android:textSize">20sp</item>
        <item name="lineHeight">26sp</item>
{fam(f)}    </style>
    <!-- Ro'yxat qatori: nom / izoh / summa -->
    <style name="TextAppearance.App.ItemTitle" parent="TextAppearance.Material3.BodyLarge">
        <item name="android:textSize">16sp</item>
        <item name="android:textColor">?attr/colorOnSurface</item>
    </style>
    <style name="TextAppearance.App.ItemSub" parent="TextAppearance.Material3.BodyMedium">
        <item name="android:textSize">13sp</item>
        <item name="android:textColor">?attr/colorOnSurfaceVariant</item>
{fam(num)}    </style>
    <style name="TextAppearance.App.ItemAmount" parent="TextAppearance.Material3.TitleMedium">
        <item name="android:textSize">16sp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:textColor">?attr/colorOnSurface</item>
{fam(num)}    </style>
    <style name="TextAppearance.App.Section" parent="TextAppearance.Material3.LabelLarge">
        <item name="android:textSize">14sp</item>
        <item name="android:textColor">?attr/colorPrimary</item>
        <item name="android:textStyle">bold</item>
{fam(f)}    </style>

    <!-- Komponentlar -->
    <style name="Widget.App.Toolbar" parent="Widget.Material3.Toolbar.Surface">
        <item name="titleTextAppearance">@style/TextAppearance.App.TitleLarge</item>
    </style>
    <style name="Widget.App.BottomNavigation" parent="Widget.Material3.BottomNavigationView">
        <item name="labelVisibilityMode">labeled</item>
        <item name="itemTextAppearanceActive">@style/TextAppearance.App.LabelMedium</item>
        <item name="itemTextAppearanceInactive">@style/TextAppearance.App.LabelMedium</item>
    </style>
    <style name="Widget.App.Button" parent="Widget.Material3.Button">
        <item name="android:minHeight">{look['btn']}dp</item>
        <item name="cornerRadius">{look['btn_radius']}dp</item>
    </style>
    <style name="Widget.App.Button.Outlined" parent="Widget.Material3.Button.OutlinedButton">
        <item name="android:minHeight">{look['btn'] - 4}dp</item>
        <item name="cornerRadius">{look['btn_radius']}dp</item>
    </style>
    <!-- Ikki-uch tanlovli guruh (Naqd/Optom, So'm/$): tanlangani aniq ko'rinadi -->
    <style name="Widget.App.Segment" parent="Widget.Material3.Button.OutlinedButton">
        <item name="android:minHeight">40dp</item>
        <item name="android:insetTop">0dp</item>
        <item name="android:insetBottom">0dp</item>
        <item name="android:paddingStart">6dp</item>
        <item name="android:paddingEnd">6dp</item>
        <item name="android:textAppearance">@style/TextAppearance.Material3.LabelLarge</item>
    </style>
    <style name="Widget.App.Chip" parent="Widget.Material3.Chip.Filter">
        <item name="chipMinTouchTargetSize">40dp</item>
        <item name="chipCornerRadius">{look['chip_radius']}dp</item>
    </style>
    <style name="Widget.App.Switch" parent="Widget.Material3.CompoundButton.MaterialSwitch">
        <item name="android:minHeight">48dp</item>
        <item name="android:textAppearance">@style/TextAppearance.Material3.BodyLarge</item>
    </style>
    <!-- Kartalar: bir xil burchak va fon -->
    <style name="Widget.App.Card" parent="Widget.Material3.CardView.{look['card']}">
        <item name="cardBackgroundColor">?attr/colorSurfaceContainerLow</item>
        <item name="cardCornerRadius">{r}dp</item>
{look['card_extra']}    </style>
    <style name="Widget.App.Card.Outlined" parent="Widget.Material3.CardView.Outlined">
        <item name="cardCornerRadius">{r}dp</item>
        <item name="strokeColor">?attr/colorOutlineVariant</item>
    </style>
    <!-- Bosh sahifadagi katta tugma-kartalar -->
    <style name="Widget.App.Card.Action" parent="Widget.Material3.CardView.{look['card']}">
        <item name="cardCornerRadius">{r}dp</item>
        <item name="android:clickable">true</item>
        <item name="android:focusable">true</item>
{look['card_extra']}    </style>
</resources>
'''


AGENT_LOOK = dict(note="Agent: oddiy va do'stona.", radius=16, btn_radius=20, chip_radius=16, btn=48,
                  font='', num='', card='Filled', card_extra='')
ADMIN_LOOK = dict(note='Admin: terminal uslubi — o\'tkir burchak, monospace raqam.', radius=6, btn_radius=6, chip_radius=6, btn=46,
                  font='monospace', num='monospace', card='Outlined',
                  card_extra='        <item name="strokeColor">?attr/colorOutlineVariant</item>\n        <item name="strokeWidth">1dp</item>\n        <item name="cardBackgroundColor">?attr/colorSurfaceContainerLow</item>\n')
MIJOZ_LOOK = dict(note='Mijoz: yumshoq va juda yumaloq.', radius=24, btn_radius=24, chip_radius=20, btn=50,
                  font='sans-serif-medium', num='', card='Filled', card_extra='')

# (res papkasi, palitra, mavzu nomi, yorug' panel?, ko'rinish, qo'shimcha ranglar, launcher foni)
# Agent — asosiy (main): R.color.brand_* kodda ishlatiladi, shuning uchun nomlar main'da turadi.
# Admin — flavor: xuddi shu nomlarni o'z qiymati bilan ustiga yozadi (Android flavor res main'dan ustun).
TARGETS = [
    ('app/src/main/res', AGENT, 'Theme.Dokon', True, AGENT_LOOK, {}, '#FFFFFF'),
    ('app/src/admin/res', ADMIN, 'Theme.Dokon', False, ADMIN_LOOK, {}, None),
    ('mijoz/src/main/res', MIJOZ, 'Theme.Mijoz', True, MIJOZ_LOOK, {'mijoz_icon_bg': ('#4B4AC4', '#4B4AC4')}, '#FFFFFF'),
]


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)


for rel, palette, theme, light, look, extra, lbg in TARGETS:
    res = os.path.join(ROOT, rel)
    write(os.path.join(res, 'values', 'colors.xml'), colors_xml(palette, 0, extra, lbg))
    write(os.path.join(res, 'values-night', 'colors.xml'), colors_xml(palette, 1, extra, lbg))
    write(os.path.join(res, 'values', 'themes.xml'), theme_xml(theme, light))
    write(os.path.join(res, 'values-night', 'themes.xml'), theme_xml(theme, False))
    write(os.path.join(res, 'values', 'styles.xml'), styles_xml(look))

print('ok')
