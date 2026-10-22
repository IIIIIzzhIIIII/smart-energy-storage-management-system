# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller 打包配置（onedir）。

构建（在 python-ems 目录下）：
    pyinstaller --noconfirm --clean python-ems.spec
产物：dist/python-ems/python-ems.exe
"""
from PyInstaller.utils.hooks import collect_all, collect_submodules

datas = []
binaries = []
hiddenimports = []

# 这些包包含编译扩展 / 动态库 / 数据文件，需要整体收集
for package in ("pyvinecopulib", "cvxpy", "statsmodels", "arch", "matplotlib"):
    pkg_datas, pkg_binaries, pkg_hidden = collect_all(package)
    datas += pkg_datas
    binaries += pkg_binaries
    hiddenimports += pkg_hidden

hiddenimports += collect_submodules("uvicorn")
hiddenimports += collect_submodules("fastapi")
hiddenimports += [
    "clarabel",
    "ecos",
    "osqp",
    "scs",
    "pandas._libs.tslibs.timedeltas",
    "pandas._libs.tslibs.period",
    "scipy._lib.array_api_compat.numpy.fft",
]

a = Analysis(
    ["server_entry.py"],
    pathex=[],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[
        "tkinter", "IPython", "pytest", "notebook", "jupyter",
        "PyQt5", "PySide6", "PyQt6", "wx", "tests",
    ],
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="python-ems",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="python-ems",
)
