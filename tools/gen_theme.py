#!/usr/bin/env python3
"""Ikki ilova uchun BITTA dizayn tizimi: ranglar (yorug'/qorong'i), mavzu va komponent uslublari.

Ranglar brend rangi #006A60 (to'q yashil-zangori) asosida Material 3 tonal palitrasidan olingan
(materialyoucolor bilan hisoblangan). Muhimi: "surface container" ranglari ham shu palitradan —
aks holda kartalar, pastki menyu, dialoglar va ro'yxat fonlari Material'ning standart BINAFSHA
tusini oladi va ranglar "sakrab" ko'rinadi.

Ishlatish: python3 tools/gen_theme.py  (app/ va mijoz/ dagi values*/colors.xml, themes.xml, styles.xml ni yozadi)
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# rol: (yorug', qorong'i)
PALETTE = {
    'primary': ('#006A60', '#53DBCA'),
    'on_primary': ('#FFFFFF', '#003732'),
    'primary_container': ('#B3EEE4', '#005048'),  # yumshoqroq (oldin #74F8E6 — juda yorqin edi)
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
    # ma'noli ranglar (ikkala ilovada bir xil)
    'debt': ('#BA1A1A', '#FFB4AB'),
    'success': ('#256C27', '#8ED885'),
    'success_container': ('#C9F2C0', '#0B3D10'),
    'on_success_container': ('#002203', '#C9F2C0'),
    'warning_container': ('#FFDEA6', '#5D4200'),
    'on_warning_container': ('#271900', '#FFDEA6'),
}

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

APPS = {
    'app': ('Theme.Dokon', {}),
    'mijoz': ('Theme.Mijoz', {'mijoz_icon_bg': ('#006A60', '#006A60')}),
}


def colors_xml(idx, extra):
    out = ['<?xml version="1.0" encoding="utf-8"?>', '<!-- tools/gen_theme.py yaratgan — qo\'lda o\'zgartirmang -->', '<resources>']
    if idx == 0:
        out.append('    <color name="ic_launcher_background">#FFFFFF</color>')
    for k, v in PALETTE.items():
        out.append(f'    <color name="brand_{k}">{v[idx]}</color>')
    for k, v in extra.items():
        out.append(f'    <color name="{k}">{v[idx]}</color>')
    out.append('</resources>')
    return '\n'.join(out) + '\n'


def theme_xml(name, dark):
    o = ['<?xml version="1.0" encoding="utf-8"?>', '<!-- tools/gen_theme.py yaratgan — qo\'lda o\'zgartirmang -->',
         '<resources xmlns:tools="http://schemas.android.com/tools">',
         f'    <style name="{name}" parent="Theme.Material3.DayNight.NoActionBar">']
    for a, c in ATTRS:
        o.append(f'        <item name="{a}">@color/brand_{c}</item>')
    o += [
        '        <item name="android:colorBackground">@color/brand_surface</item>',
        '        <item name="android:windowBackground">@color/brand_surface</item>',
        '        <item name="android:statusBarColor">@color/brand_surface</item>',
        '        <item name="android:navigationBarColor">@color/brand_surface_container</item>',
        f'        <item name="android:windowLightStatusBar">{"false" if dark else "true"}</item>',
        f'        <item name="android:windowLightNavigationBar" tools:targetApi="27">{"false" if dark else "true"}</item>',
        # matn o'lchamlari: kichik matnlar telefonda o'qiladigan bo'lsin
        '        <item name="textAppearanceBodySmall">@style/TextAppearance.App.BodySmall</item>',
        '        <item name="textAppearanceLabelSmall">@style/TextAppearance.App.LabelSmall</item>',
        '        <item name="textAppearanceLabelMedium">@style/TextAppearance.App.LabelMedium</item>',
        '        <item name="textAppearanceTitleLarge">@style/TextAppearance.App.TitleLarge</item>',
        # komponentlar
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


STYLES = '''<?xml version="1.0" encoding="utf-8"?>
<!-- tools/gen_theme.py yaratgan — qo'lda o'zgartirmang. Ikki ilovada bir xil. -->
<resources>
    <!-- Matn -->
    <style name="TextAppearance.App.BodySmall" parent="TextAppearance.Material3.BodySmall">
        <item name="android:textSize">13sp</item>
        <item name="lineHeight">18sp</item>
    </style>
    <style name="TextAppearance.App.LabelSmall" parent="TextAppearance.Material3.LabelSmall">
        <item name="android:textSize">12sp</item>
    </style>
    <style name="TextAppearance.App.LabelMedium" parent="TextAppearance.Material3.LabelMedium">
        <item name="android:textSize">13sp</item>
    </style>
    <style name="TextAppearance.App.TitleLarge" parent="TextAppearance.Material3.TitleLarge">
        <item name="android:textSize">20sp</item>
        <item name="lineHeight">26sp</item>
    </style>
    <!-- Ro'yxat qatori: nom / izoh / summa -->
    <style name="TextAppearance.App.ItemTitle" parent="TextAppearance.Material3.BodyLarge">
        <item name="android:textSize">16sp</item>
        <item name="android:textColor">?attr/colorOnSurface</item>
    </style>
    <style name="TextAppearance.App.ItemSub" parent="TextAppearance.Material3.BodyMedium">
        <item name="android:textSize">13sp</item>
        <item name="android:textColor">?attr/colorOnSurfaceVariant</item>
    </style>
    <style name="TextAppearance.App.ItemAmount" parent="TextAppearance.Material3.TitleMedium">
        <item name="android:textSize">16sp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:textColor">?attr/colorOnSurface</item>
    </style>
    <style name="TextAppearance.App.Section" parent="TextAppearance.Material3.LabelLarge">
        <item name="android:textSize">14sp</item>
        <item name="android:textColor">?attr/colorPrimary</item>
        <item name="android:textStyle">bold</item>
    </style>

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
        <item name="android:minHeight">48dp</item>
    </style>
    <style name="Widget.App.Button.Outlined" parent="Widget.Material3.Button.OutlinedButton">
        <item name="android:minHeight">44dp</item>
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
    </style>
    <style name="Widget.App.Switch" parent="Widget.Material3.CompoundButton.MaterialSwitch">
        <item name="android:minHeight">48dp</item>
        <item name="android:textAppearance">@style/TextAppearance.Material3.BodyLarge</item>
    </style>
    <!-- Kartalar: bir xil burchak va fon -->
    <style name="Widget.App.Card" parent="Widget.Material3.CardView.Filled">
        <item name="cardBackgroundColor">?attr/colorSurfaceContainerLow</item>
        <item name="cardCornerRadius">16dp</item>
    </style>
    <style name="Widget.App.Card.Outlined" parent="Widget.Material3.CardView.Outlined">
        <item name="cardCornerRadius">16dp</item>
        <item name="strokeColor">?attr/colorOutlineVariant</item>
    </style>
    <!-- Bosh sahifadagi katta tugma-kartalar -->
    <style name="Widget.App.Card.Action" parent="Widget.Material3.CardView.Filled">
        <item name="cardCornerRadius">16dp</item>
        <item name="android:clickable">true</item>
        <item name="android:focusable">true</item>
    </style>
</resources>
'''


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)


for app, (theme, extra) in APPS.items():
    res = os.path.join(ROOT, app, 'src', 'main', 'res')
    write(os.path.join(res, 'values', 'colors.xml'), colors_xml(0, extra))
    write(os.path.join(res, 'values-night', 'colors.xml'), colors_xml(1, extra))
    write(os.path.join(res, 'values', 'themes.xml'), theme_xml(theme, False))
    write(os.path.join(res, 'values-night', 'themes.xml'), theme_xml(theme, True))
    write(os.path.join(res, 'values', 'styles.xml'), STYLES)
print('ok')
