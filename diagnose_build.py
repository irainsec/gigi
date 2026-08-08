import subprocess
import os

print(f"Current Working Directory: {os.getcwd()}")
env = os.environ.copy()
env["JAVA_HOME"] = "C:\\Program Files\\Android\\Android Studio\\jbr"
# Add Java to PATH
env["PATH"] = os.path.join(env["JAVA_HOME"], "bin") + os.pathsep + env["PATH"]

cmd = [".\\gradlew.bat", ":app:compileReleaseKotlin", "--no-daemon"]
print(f"Running: {' '.join(cmd)}")

process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, cwd="c:\\Users\\Threat Hunting\\OneDrive - AltiSec Technologies Pvt. Ltd\\Desktop\\aman\\gigi", env=env)

for line in process.stdout:
    if "error:" in line.lower() or "e:" in line.lower():
        print(line, end="")
    elif "compilation error" in line.get_text().lower() if hasattr(line, 'get_text') else "compilation error" in line.lower():
        print(line, end="")

process.wait()
if process.returncode != 0:
    print(f"\nBuild FAILED with return code {process.returncode}")
else:
    print("\nBuild SUCCESSFUL")
