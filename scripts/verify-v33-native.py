#!/usr/bin/env python3
"""Build and execute a committed V33 fixture in a fresh non-root workspace."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import signal
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n")


def run(out, name, command, cwd, env, timeout=1800):
    write_json(out / f"{name}.command.json", command)
    result = {"startedAt": datetime.now(timezone.utc).isoformat(), "exitStatus": None}
    print(f"{name}: {out / (name + '.log')}", flush=True)
    try:
        with (out / f"{name}.log").open("w") as log:
            with subprocess.Popen(command, cwd=cwd, env=env, stdout=log,
                                  stderr=subprocess.STDOUT, start_new_session=True) as process:
                try:
                    result["exitStatus"] = process.wait(timeout=timeout)
                except (subprocess.TimeoutExpired, KeyboardInterrupt):
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                    raise
        if result["exitStatus"]:
            raise subprocess.CalledProcessError(result["exitStatus"], command)
    except (OSError, subprocess.SubprocessError, KeyboardInterrupt) as error:
        result["error"] = str(error)
        raise
    finally:
        result["endedAt"] = datetime.now(timezone.utc).isoformat()
        write_json(out / f"{name}.result.json", result)


def checksum(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ref", default="HEAD", help="committed source revision; excludes working-tree edits")
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--work-root", type=Path, default=ROOT / "target/v33-native-runs")
    parser.add_argument("--seed-repository", type=Path,
                        help="optional read-only source cache; copied into the new writable Maven repository")
    args = parser.parse_args()
    if os.geteuid() == 0:
        parser.error("Run as your normal user with python3, not sudo.")
    if args.java_home is None:
        parser.error("Set JAVA_HOME to GraalVM or pass --java-home.")
    java_home = args.java_home.expanduser().resolve()
    if not (java_home / "bin/native-image").is_file():
        parser.error(f"No native-image in {java_home}/bin")
    seed = args.seed_repository.expanduser().resolve() if args.seed_repository else None
    if seed is not None and not seed.is_dir():
        parser.error(f"Seed repository does not exist: {seed}")
    commit = subprocess.check_output(["git", "rev-parse", "--verify", args.ref + "^{commit}"],
                                     cwd=ROOT, text=True).strip()
    os.umask(0o077)
    args.work_root.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="native-", dir=args.work_root.resolve()))
    out = work / "evidence"
    out.mkdir()
    source = work / "source"
    repository = work / "repository"
    env = dict(os.environ, JAVA_HOME=str(java_home), MAVEN_OPTS="-Xmx512m -XX:ActiveProcessorCount=2")
    env["PATH"] = str(java_home / "bin") + os.pathsep + env["PATH"]
    summary = {"commit": commit, "status": "failed", "nativeCompiled": False,
               "nativeExecuted": False, "repository": str(repository), "seedRepository": str(seed) if seed else None}
    shutil.copyfile(__file__, out / "runner.py")
    (out / "invoking-worktree-status.txt").write_bytes(
        subprocess.check_output(["git", "status", "--short"], cwd=ROOT))
    print(f"Fresh workspace: {work}\nCommitted source: {commit}", flush=True)
    try:
        run(out, "socket-preflight", [sys.executable, "-c",
            'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); s.close()'], ROOT, env, 10)
        for name, command in [("java", ["java", "-version"]), ("native-toolchain", ["native-image", "--version"]),
                              ("maven", ["mvn", "-version"]), ("resources-before", ["free", "-h"])]:
            run(out, name, command, ROOT, env)
        run(out, "clone", ["git", "clone", "--local", "--no-hardlinks", "--no-checkout", str(ROOT), str(source)], ROOT, env)
        run(out, "checkout", ["git", "checkout", "--detach", commit], source, env)
        status = subprocess.check_output(["git", "status", "--porcelain"], cwd=source)
        (out / "source-status-before.txt").write_bytes(status)
        if status.strip():
            raise RuntimeError("Source checkout is not clean")
        (out / "source-commit.txt").write_text(commit + "\n")
        (out / "source-tree.txt").write_bytes(subprocess.check_output(["git", "rev-parse", "HEAD^{tree}"], cwd=source))
        if seed:
            repository.mkdir()
            # Do not preserve ownership or restrictive cache modes in this new user-owned directory.
            run(out, "seed-repository", ["cp", "-RL", "--reflink=auto", "--no-preserve=mode,ownership",
                                         str(seed) + "/.", str(repository)], ROOT, env)
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        version = ET.parse(source / "pom.xml").find("m:version", ns).text
        maven = ["mvn", "-B", "-ntp", "-s", str(source / ".mvn/maven-central-settings.xml"),
                 f"-Dmaven.repo.local={repository}"]
        run(out, "install", maven + ["-DskipTests", "-Dmaven.javadoc.skip=true", "install"], source, env)
        fixture = maven + ["-f", ".github/native-smoke/pom.xml", f"-Dreactive-http-client.version={version}"]
        run(out, "compile", fixture + ["-Pnative", "-DbuildArgs=-H:+SharedArenaSupport,-J-Xmx6g,--parallelism=2",
                                       "clean", "native:compile"], source, env)
        binary = source / ".github/native-smoke/target/reactive-http-client-native-smoke"
        summary["nativeCompiled"] = True
        summary["binarySha256"] = checksum(binary)
        (out / "binary-sha256.txt").write_text(f"{summary['binarySha256']}  {binary.name}\n")
        shutil.copy2(binary, out / binary.name)
        run(out, "execute", ["timeout", "180", str(binary)], source, env, 200)
        summary["nativeExecuted"] = True
        for name, goal, prop, file in [
                ("effective-pom", "help:effective-pom", "output", "effective-pom.xml"),
                ("dependencies", "dependency:tree", "outputFile", "dependency-tree.txt"),
                ("classpath", "dependency:build-classpath", "mdep.outputFile", "classpath.txt")]:
            run(out, name, fixture + [goal, f"-D{prop}={out / file}"], source, env)
        run(out, "resources-after", ["free", "-h"], source, env)
        status = subprocess.check_output(["git", "status", "--porcelain"], cwd=source)
        (out / "source-status-after.txt").write_bytes(status)
        if status.strip():
            raise RuntimeError("Source changed during verification")
        summary["status"] = "passed"
    except (OSError, RuntimeError, subprocess.SubprocessError, KeyboardInterrupt) as error:
        summary["error"] = str(error)
        print(f"Native gate not complete: {error}\nDo not retry with sudo.", file=sys.stderr)
    finally:
        reports = source / ".github/native-smoke/target/surefire-reports"
        if reports.is_dir():
            shutil.copytree(reports, out / "surefire-reports")
        write_json(out / "summary.json", summary)
        files = sorted(p for p in out.rglob("*") if p.is_file())
        (out / "SHA256SUMS").write_text("".join(f"{checksum(p)}  {p.relative_to(out)}\n" for p in files))
        print(f"Evidence: {out}\nNative status: {summary['status']}", flush=True)
    return 0 if summary["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
