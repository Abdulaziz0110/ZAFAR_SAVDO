"""Sozlamalar maketlari uchun bir xil qator/bo'lim bloklari (tools/gen_extras_layout.py ishlatadi)."""

def section(title):
    return f'''            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginStart="4dp"
                android:layout_marginTop="20dp"
                android:layout_marginBottom="6dp"
                android:text="{title}"
                android:textAppearance="@style/TextAppearance.App.Section" />
'''

def card_open():
    return '''            <com.google.android.material.card.MaterialCardView
                style="@style/Widget.App.Card"
                android:layout_width="match_parent"
                android:layout_height="wrap_content">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:paddingTop="6dp"
                    android:paddingBottom="6dp">
'''

CARD_CLOSE = '''                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
'''

def switch_row(id_, title, hint=None):
    h = f'''
                        <TextView
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginEnd="64dp"
                            android:text="{hint}"
                            android:textAppearance="@style/TextAppearance.App.ItemSub" />''' if hint else ''
    return f'''                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:paddingStart="16dp"
                        android:paddingTop="2dp"
                        android:paddingEnd="16dp"
                        android:paddingBottom="6dp">

                        <com.google.android.material.materialswitch.MaterialSwitch
                            android:id="@+id/{id_}"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:text="{title}" />{h}
                    </LinearLayout>
'''

def label(t, hint=None, top='10dp'):
    h = f'''
                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="16dp"
                        android:layout_marginEnd="16dp"
                        android:text="{hint}"
                        android:textAppearance="@style/TextAppearance.App.ItemSub" />
''' if hint else ''
    return f'''                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="16dp"
                        android:layout_marginTop="{top}"
                        android:layout_marginEnd="16dp"
                        android:text="{t}"
                        android:textAppearance="@style/TextAppearance.App.ItemTitle" />
''' + h

def hint(t):
    return f'''                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="16dp"
                        android:layout_marginTop="2dp"
                        android:layout_marginEnd="16dp"
                        android:layout_marginBottom="6dp"
                        android:text="{t}"
                        android:textAppearance="@style/TextAppearance.App.ItemSub" />
'''

def seg_group(id_, items):
    btns = ''.join(f'''
                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/{bid}"
                            style="@style/Widget.App.Segment"
                            android:layout_width="0dp"
                            android:layout_height="44dp"
                            android:layout_weight="1"
                            android:text="{t}" />
''' for bid, t in items)
    return f'''                    <com.google.android.material.button.MaterialButtonToggleGroup
                        android:id="@+id/{id_}"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="16dp"
                        android:layout_marginTop="6dp"
                        android:layout_marginEnd="16dp"
                        android:layout_marginBottom="10dp"
                        app:selectionRequired="true"
                        app:singleSelection="true">
{btns}                    </com.google.android.material.button.MaterialButtonToggleGroup>
'''

def buttons(items):
    """[(id, text, style, icon)] bir qatorda."""
    out = '''                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="16dp"
                        android:layout_marginTop="4dp"
                        android:layout_marginEnd="16dp"
                        android:layout_marginBottom="10dp"
                        android:orientation="horizontal">
'''
    for i, (bid, t, style, icon) in enumerate(items):
        m = '' if len(items) == 1 else ('\n                            android:layout_marginEnd="6dp"' if i == 0 else '\n                            android:layout_marginStart="6dp"')
        ic = f'\n                            app:icon="@drawable/{icon}"' if icon else ''
        out += f'''
                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/{bid}"
                            style="{style}"
                            android:layout_width="0dp"
                            android:layout_height="48dp"{m}
                            android:layout_weight="1"
                            android:text="{t}"{ic} />
'''
    return out + '                    </LinearLayout>\n'

def screen(title, body):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- tools/gen_extras_layout.py yaratgan -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?android:attr/colorBackground"
    android:clickable="true"
    android:focusable="true"
    android:orientation="vertical">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        app:navigationIcon="@drawable/ic_arrow_back"
        app:title="{title}" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingStart="16dp"
            android:paddingTop="4dp"
            android:paddingEnd="16dp"
            android:paddingBottom="24dp">
{body}        </LinearLayout>
    </ScrollView>
</LinearLayout>
'''

def text(id_, style='?attr/textAppearanceBodyMedium', top='6dp', color=None, gone=False, extra=''):
    c = f'\n                        android:textColor="{color}"' if color else ''
    g = '\n                        android:visibility="gone"' if gone else ''
    i = f'\n                        android:id="@+id/{id_}"' if id_ else ''
    return f'''                    <TextView{i}
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="16dp"
                        android:layout_marginTop="{top}"
                        android:layout_marginEnd="16dp"
                        android:lineSpacingExtra="2dp"{extra}
                        android:textAppearance="{style}"{c}{g} />
'''

def container(id_):
    return f'''                    <LinearLayout
                        android:id="@+id/{id_}"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:orientation="vertical" />
'''

def card_title(t):
    return f'''                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="16dp"
                        android:layout_marginTop="10dp"
                        android:layout_marginEnd="16dp"
                        android:text="{t}"
                        android:textAppearance="@style/TextAppearance.App.Section" />
'''

def field(til_id, edt_id, hint, input_type='text', helper=None):
    h = f'\n                        app:helperText="{helper}"' if helper else ''
    return f'''                    <com.google.android.material.textfield.TextInputLayout
                        android:id="@+id/{til_id}"
                        style="@style/Widget.Material3.TextInputLayout.OutlinedBox.Dense"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="16dp"
                        android:layout_marginTop="8dp"
                        android:layout_marginEnd="16dp"
                        android:hint="{hint}"{h}>

                        <com.google.android.material.textfield.TextInputEditText
                            android:id="@+id/{edt_id}"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:inputType="{input_type}"
                            android:maxLines="2" />
                    </com.google.android.material.textfield.TextInputLayout>
'''

def screen_progress(title, body, menu=None):
    m = f'\n        app:menu="@menu/{menu}"' if menu else ''
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- tools/gen_share_layouts.py yaratgan -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?android:attr/colorBackground"
    android:clickable="true"
    android:focusable="true"
    android:orientation="vertical">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        app:navigationIcon="@drawable/ic_arrow_back"{m}
        app:title="{title}" />

    <com.google.android.material.progressindicator.LinearProgressIndicator
        android:id="@+id/progress"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:indeterminate="true"
        android:visibility="invisible" />

    <ScrollView
        android:id="@+id/scroll"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingStart="16dp"
            android:paddingTop="4dp"
            android:paddingEnd="16dp"
            android:paddingBottom="24dp">
{body}        </LinearLayout>
    </ScrollView>
</LinearLayout>
'''
