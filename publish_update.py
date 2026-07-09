import os
import sys
import subprocess
import re
import shutil
import json

def locate_java_home():
    if "JAVA_HOME" in os.environ and os.path.exists(os.environ["JAVA_HOME"]):
        return
    possible_paths = [
        os.path.join("..", "Android anme", "jdk"),
        os.path.join("..", "Android anime", "jdk"),
        "C:\\Users\\RG\\Desktop\\Android anme\\jdk",
        "C:\\Users\\RG\\Desktop\\Android anime\\jdk"
    ]
    for p in possible_paths:
        abs_p = os.path.abspath(p)
        if os.path.exists(abs_p):
            try:
                subdirs = [d for d in os.listdir(abs_p) if os.path.isdir(os.path.join(abs_p, d)) and d.startswith("jdk")]
                if subdirs:
                    java_home = os.path.join(abs_p, subdirs[0])
                    os.environ["JAVA_HOME"] = java_home
                    print(f"Automatically set JAVA_HOME to: {java_home}")
                    return
            except Exception:
                pass

def get_git_info():
    try:
        # Get remote URL
        remote_url = subprocess.check_output(["git", "remote", "get-url", "origin"], stderr=subprocess.DEVNULL).decode("utf-8").strip()
        branch = subprocess.check_output(["git", "branch", "--show-current"], stderr=subprocess.DEVNULL).decode("utf-8").strip()
        
        # Parse github username and repository
        # Matches: https://github.com/username/repo.git or git@github.com:username/repo.git or without .git
        match = re.search(r"github\.com[:/]([^/]+)/([^.]+)(?:\.git)?", remote_url)
        if match:
            username = match.group(1)
            repo = match.group(2)
            return username, repo, branch
    except Exception:
        pass
    
    # Fallback to manual configuration
    print("Could not automatically determine Git remote info.")
    username = input("Enter your GitHub username (e.g. anshsingh70007-hash): ").strip()
    repo = input("Enter your GitHub repository name (e.g. Aniflow): ").strip()
    branch = input("Enter repository branch [main]: ").strip() or "main"
    return username, repo, branch

def get_app_version():
    gradle_file_path = "app/build.gradle.kts"
    if not os.path.exists(gradle_file_path):
        print(f"Error: {gradle_file_path} not found.")
        sys.exit(1)
        
    with open(gradle_file_path, "r", encoding="utf-8") as f:
        content = f.read()
        
    version_code_match = re.search(r"versionCode\s*=\s*(\d+)", content)
    version_name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    
    if not version_code_match or not version_name_match:
        print("Error: Could not parse versionCode or versionName from app/build.gradle.kts")
        sys.exit(1)
        
    return int(version_code_match.group(1)), version_name_match.group(1), content

def update_gradle_version(new_code, new_name, original_content):
    gradle_file_path = "app/build.gradle.kts"
    content = original_content
    content = re.sub(r"versionCode\s*=\s*(\d+)", f"versionCode = {new_code}", content)
    content = re.sub(r'versionName\s*=\s*"([^"]+)"', f'versionName = "{new_name}"', content)
    
    with open(gradle_file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Updated app/build.gradle.kts to Version {new_name} (Build {new_code})")

def update_config_kt(username, repo, branch):
    config_path = "app/src/main/java/com/example/aniflow/utils/UpdateConfig.kt"
    os.makedirs(os.path.dirname(config_path), exist_ok=True)
    
    url = f"https://raw.githubusercontent.com/{username}/{repo}/{branch}/app_update.json"
    content = f"""package com.example.aniflow.utils

object UpdateConfig {{
    const val UPDATE_JSON_URL = "{url}"
}}
"""
    with open(config_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Updated UpdateConfig.kt with update JSON URL: {url}")

def build_apk():
    print("Building app APK using Gradle...")
    gradlew = "gradlew.bat" if os.name == "nt" else "./gradlew"
    
    # Run assembleDebug or assembleRelease. We use assembleRelease by default, or debug for ease of use.
    # Let's ask the user
    build_type = input("Select Build Type: [1] Debug (default)  [2] Release: ").strip()
    task = "assembleRelease" if build_type == "2" else "assembleDebug"
    
    # Run the gradle build command
    result = subprocess.run([gradlew, task], shell=True if os.name == "nt" else False)
    if result.returncode != 0:
        print("Gradle build failed. Please fix compile errors before publishing updates.")
        sys.exit(1)
        
    apk_source = (
        "app/build/outputs/apk/release/app-release-unsigned.apk" if task == "assembleRelease" and os.path.exists("app/build/outputs/apk/release/app-release-unsigned.apk")
        else "app/build/outputs/apk/release/app-release.apk" if task == "assembleRelease"
        else "app/build/outputs/apk/debug/app-debug.apk"
    )
    
    if not os.path.exists(apk_source):
        # Search for any built apk under outputs
        found_apks = []
        for root, dirs, files in os.walk("app/build/outputs/apk"):
            for file in files:
                if file.endswith(".apk"):
                    found_apks.append(os.path.join(root, file))
        if found_apks:
            apk_source = found_apks[0]
        else:
            print("Error: Could not find built APK file.")
            sys.exit(1)
            
    return apk_source

def main():
    locate_java_home()
    print("=== AniFlow App Update Publisher ===")
    
    username, repo, branch = get_git_info()
    print(f"Target Repository: {username}/{repo} (Branch: {branch})")
    
    version_code, version_name, original_content = get_app_version()
    print(f"Current version: {version_name} (Build code: {version_code})")
    
    increment = input("Do you want to increment version? [Y/n]: ").strip().lower()
    new_code = version_code
    new_name = version_name
    
    if increment != "n":
        new_code = version_code + 1
        suggested_name = ".".join(version_name.split(".")[:-1]) + f".{int(version_name.split('.')[-1]) + 1}" if "." in version_name else version_name
        new_name_input = input(f"Enter new version name [{suggested_name}]: ").strip()
        new_name = new_name_input if new_name_input else suggested_name
        update_gradle_version(new_code, new_name, original_content)
        
    update_config_kt(username, repo, branch)
    
    apk_source = build_apk()
    
    # Define releases directory and copy APK
    releases_dir = "releases"
    os.makedirs(releases_dir, exist_ok=True)
    apk_name = f"AniFinal_{new_name}.apk"
    apk_dest = os.path.join(releases_dir, apk_name)
    shutil.copy2(apk_source, apk_dest)
    print(f"Saved update APK to: {apk_dest}")
    
    # Prompt for update notes
    notes = input("Enter update notes / changes: ").strip() or "General bug fixes and performance improvements."
    
    # Generate app_update.json
    update_url = f"https://raw.githubusercontent.com/{username}/{repo}/{branch}/releases/{apk_name}"
    update_data = {
        "versionCode": new_code,
        "versionName": new_name,
        "updateUrl": update_url,
        "updateNotes": notes,
        "forceUpdate": False,
        "silentUpdate": False
    }
    
    with open("app_update.json", "w", encoding="utf-8") as f:
        json.dump(update_data, f, indent=2)
    print("Generated app_update.json")
    
    # Git commit and push helper
    publish_git = input("Do you want to commit and push changes to GitHub now? [Y/n]: ").strip().lower()
    if publish_git != "n":
        print("Staging and pushing files...")
        subprocess.run(["git", "add", "app/build.gradle.kts", "app/src/main/java/com/example/aniflow/utils/UpdateConfig.kt", "app_update.json", apk_dest])
        subprocess.run(["git", "commit", "-m", f"Release Update v{new_name}"])
        result = subprocess.run(["git", "push", "origin", branch])
        if result.returncode == 0:
            print("\nSUCCESS: Update published successfully! Users will see the update notification when checking.")
        else:
            print("\nWARNING: Git push failed. Please push manually to publish the update.")
    else:
        print("\nSkipped git push. To manually publish, push these files to GitHub:")
        print(f"- app_update.json")
        print(f"- {apk_dest}")
        print(f"- app/src/main/java/com/example/aniflow/utils/UpdateConfig.kt")

if __name__ == "__main__":
    main()
