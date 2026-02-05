#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
获取 Android 发布版 SHA1 指纹的工具脚本

使用方法：
1. 如果已有 release keystore：
   python get_release_sha1.py

2. 如果还没有 release keystore，需要先创建：
   keytool -genkey -v -keystore release.keystore -alias my-key-alias -keyalg RSA -keysize 2048 -validity 10000

3. 然后使用此脚本获取 SHA1
"""

import os
import subprocess
import sys

def print_instructions():
    """打印获取 SHA1 的说明"""
    print("=" * 70)
    print("获取 Android 发布版 SHA1 指纹")
    print("=" * 70)
    print()
    print("有两种方式获取 SHA1：")
    print()
    print("方式1：使用 keytool 命令（推荐）")
    print("-" * 70)
    print("命令格式：")
    print("  keytool -list -v -keystore <keystore路径> -alias <别名>")
    print()
    print("示例：")
    print("  keytool -list -v -keystore release.keystore -alias my-key-alias")
    print()
    print("输入密码后，会看到类似输出：")
    print("  证书指纹:")
    print("    SHA1: AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12")
    print("    SHA256: ...")
    print()
    print("复制 SHA1 后面的值即可")
    print()
    print("=" * 70)
    print()
    print("方式2：从 Android Studio 获取")
    print("-" * 70)
    print("1. 打开 Android Studio")
    print("2. 点击右侧 Gradle 面板")
    print("3. 展开 app -> Tasks -> android")
    print("4. 双击 signingReport")
    print("5. 在 Run 窗口查看输出，找到 'Variant: release' 部分")
    print("6. 复制 SHA1 值")
    print()
    print("=" * 70)
    print()
    print("方式3：使用 gradlew 命令")
    print("-" * 70)
    print("在项目根目录执行：")
    print("  gradlew signingReport")
    print()
    print("查看输出中的 'Variant: release' 部分的 SHA1")
    print()
    print("=" * 70)
    print()

def check_keystore_exists():
    """检查常见位置的 keystore 文件"""
    print("检查常见的 keystore 位置...")
    print()
    
    common_paths = [
        "release.keystore",
        "app/release.keystore",
        os.path.expanduser("~/.android/release.keystore"),
        os.path.expanduser("~/release.keystore"),
    ]
    
    found = False
    for path in common_paths:
        if os.path.exists(path):
            print(f"[FOUND] 找到 keystore: {path}")
            found = True
    
    if not found:
        print("[NOT FOUND] 未找到 release.keystore 文件")
        print()
        print("如果还没有创建 release keystore，请先创建：")
        print()
        print("keytool -genkey -v -keystore release.keystore -alias my-key-alias \\")
        print("        -keyalg RSA -keysize 2048 -validity 10000")
        print()
    
    print()

def run_keytool_command():
    """尝试运行 keytool 命令"""
    print("尝试使用 keytool 获取 SHA1...")
    print()
    
    # 检查 keytool 是否可用
    try:
        result = subprocess.run(['keytool', '-help'], 
                              capture_output=True, 
                              text=True,
                              timeout=5)
        if result.returncode == 0:
            print("[OK] keytool 命令可用")
            print()
            print("请手动执行以下命令（需要输入 keystore 密码）：")
            print()
            print("keytool -list -v -keystore <你的keystore路径> -alias <你的别名>")
            print()
        else:
            raise Exception("keytool 不可用")
    except Exception as e:
        print("[ERROR] keytool 命令不可用或未找到")
        print()
        print("请确保已安装 JDK 并配置了环境变量")
        print()

def main():
    """主函数"""
    print_instructions()
    check_keystore_exists()
    run_keytool_command()
    
    print("=" * 70)
    print("注意事项：")
    print("=" * 70)
    print("1. 天地图需要的是 '发布版SHA1'，不是 debug 版的")
    print("2. SHA1 格式：XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX")
    print("3. 共20组，用冒号分隔")
    print("4. 包名可以在 AndroidManifest.xml 中找到")
    print("=" * 70)
    print()
    
    # 读取包名
    manifest_path = "app/src/main/AndroidManifest.xml"
    if os.path.exists(manifest_path):
        try:
            with open(manifest_path, 'r', encoding='utf-8') as f:
                content = f.read()
                if 'package=' in content:
                    import re
                    match = re.search(r'package="([^"]+)"', content)
                    if match:
                        package_name = match.group(1)
                        print(f"当前应用包名: {package_name}")
                        print()
        except Exception as e:
            pass

if __name__ == "__main__":
    main()