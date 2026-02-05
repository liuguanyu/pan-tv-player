import os
import subprocess
from pathlib import Path

def find_jdk_locations():
    """查找系统中所有可能的JDK位置"""
    print("正在查找JDK安装位置...\n")
    
    jdk_locations = []
    
    # 常见的JDK安装路径
    common_paths = [
        # Android Studio 自带JDK
        r"C:\Program Files\Android\Android Studio\jbr",
        r"C:\Program Files\Android\Android Studio\jre",
        r"C:\Program Files (x86)\Android\Android Studio\jbr",
        r"C:\Program Files (x86)\Android\Android Studio\jre",
        
        # 用户目录下的Android Studio
        os.path.expanduser(r"~\AppData\Local\Android\Sdk"),
        os.path.expanduser(r"~\AppData\Local\JetBrains"),
        
        # 独立安装的JDK
        r"C:\Program Files\Java",
        r"C:\Program Files (x86)\Java",
        r"C:\Program Files\Eclipse Adoptium",
        r"C:\Program Files\AdoptOpenJDK",
        
        # Oracle JDK
        r"C:\Program Files\Oracle\Java",
    ]
    
    # 搜索每个路径
    for base_path in common_paths:
        if os.path.exists(base_path):
            print(f"检查路径: {base_path}")
            # 递归查找 java.exe
            for root, dirs, files in os.walk(base_path):
                if 'java.exe' in files:
                    java_path = os.path.join(root, 'java.exe')
                    # 获取版本信息
                    try:
                        result = subprocess.run(
                            [java_path, '-version'],
                            capture_output=True,
                            text=True,
                            timeout=5
                        )
                        version_info = result.stderr.split('\n')[0] if result.stderr else "未知版本"
                        jdk_locations.append({
                            'path': root,
                            'java_exe': java_path,
                            'version': version_info
                        })
                    except:
                        jdk_locations.append({
                            'path': root,
                            'java_exe': java_path,
                            'version': "无法获取版本"
                        })
    
    return jdk_locations

def main():
    jdk_locations = find_jdk_locations()
    
    print("\n" + "="*60)
    if jdk_locations:
        print(f"找到 {len(jdk_locations)} 个JDK安装:\n")
        for i, jdk in enumerate(jdk_locations, 1):
            print(f"[{i}]")
            print(f"  路径: {jdk['path']}")
            print(f"  版本: {jdk['version']}")
            print(f"  java.exe: {jdk['java_exe']}")
            print()
        
        # 推荐使用的JDK
        print("="*60)
        print("推荐操作：")
        print("\n方案1：在Android Studio内置Terminal中运行（推荐）")
        print("  1. 打开Android Studio")
        print("  2. 打开本项目")
        print("  3. 点击底部的 Terminal 选项卡")
        print("  4. 直接运行: gradlew signingReport")
        print("     （Android Studio的Terminal会自动配置JDK）")
        
        if jdk_locations:
            print(f"\n方案2：手动设置JAVA_HOME环境变量")
            print(f"  推荐使用: {jdk_locations[0]['path']}")
            print(f"\n  Windows设置步骤:")
            print(f"  1. 右键 '此电脑' -> '属性'")
            print(f"  2. 点击 '高级系统设置'")
            print(f"  3. 点击 '环境变量'")
            print(f"  4. 在 '系统变量' 中点击 '新建'")
            print(f"  5. 变量名: JAVA_HOME")
            print(f"  6. 变量值: {jdk_locations[0]['path']}")
            print(f"  7. 确定并重启命令行窗口")
            
            print(f"\n方案3：临时设置（本次会话有效）")
            print(f"  在命令行中执行:")
            print(f'  set "JAVA_HOME={jdk_locations[0]["path"]}"')
            print(f'  set "PATH=%JAVA_HOME%\\bin;%PATH%"')
            print(f"  gradlew signingReport")
    else:
        print("未找到JDK安装")
        print("\n建议:")
        print("1. 确保已安装Android Studio")
        print("2. 或下载安装JDK: https://adoptium.net/")
        print("3. 然后重新运行此脚本")
    
    print("="*60)

if __name__ == "__main__":
    main()