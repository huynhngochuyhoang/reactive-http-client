#!/usr/bin/env python3
"""V33 assembled-artifact evidence; native compilation is a separate clean-source gate."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from datetime import datetime, timezone
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("stage", choices=["install", "mock", "docs", "aot-tests", "aot-tests41", "consumer40", "consumer41",
                                      "minimal40", "minimal41", "jvm40", "jvm41"])
parser.add_argument("--repository", type=Path, default=Path.home() / ".m2/repository")
parser.add_argument("--evidence", type=Path, default=ROOT / "target/release-evidence/v33/priority7")
args = parser.parse_args()
out = args.evidence.resolve() / args.stage
out.mkdir(parents=True, exist_ok=False)
maven = ["mvn", "-B", "-ntp", "-s", str(ROOT / ".mvn/maven-central-settings.xml"),
         f"-Dmaven.repo.local={args.repository.resolve()}"]
commands = []


def run(command):
    index = len(commands)
    commands.append(command)
    (out / "commands.json").write_text(json.dumps(commands, indent=2) + "\n")
    with (out / f"{index:02d}.log").open("w") as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=1200)
    (out / f"{index:02d}.exit-status").write_text(str(result.returncode) + "\n")
    print((out / f"{index:02d}.log").read_text()[-2500:], flush=True)
    result.check_returncode()


def reports(directory, label):
    target = out / label
    target.mkdir()
    for source in directory.glob("TEST-*.xml"):
        shutil.copy2(source, target)
    totals = {key: sum(int(ET.parse(p).getroot().get(key, 0)) for p in target.glob("*.xml"))
              for key in ("tests", "failures", "errors", "skipped")}
    (out / f"{label}.json").write_text(json.dumps(totals, indent=2) + "\n")
    assert totals["tests"] > 0 and not any(totals[k] for k in ("failures", "errors", "skipped")), totals


def overlay(source, boot):
    target = out / "fixture"
    shutil.copytree(ROOT / source, target, ignore=shutil.ignore_patterns("target"))
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    ET.register_namespace("", ns["m"])
    pom = ET.parse(target / "pom.xml")
    pom.find("m:parent/m:version", ns).text = boot
    version = pom.find("m:properties/m:spring-boot.version", ns)
    if version is not None:
        version.text = boot
    pom.write(target / "pom.xml", encoding="utf-8", xml_declaration=True)
    return target


def artifacts(command, boot, minimal=False):
    run(command + ["help:effective-pom", f"-Doutput={out / 'effective-pom.xml'}"])
    run(command + ["dependency:tree", f"-DoutputFile={out / 'dependency-tree.txt'}"])
    run(command + ["dependency:build-classpath", f"-Dmdep.outputFile={out / 'classpath.txt'}"])
    jars = [Path(p) for p in (out / "classpath.txt").read_text().strip().split(os.pathsep)]
    assert jars and all(p.is_file() and p.suffix == ".jar" for p in jars)
    boots = [p for p in jars if "/org/springframework/boot/" in str(p)]
    assert boots and all(p.parent.name == boot for p in boots), boots
    starters = [p for p in jars if p.name == "reactive-http-client-starter-4.5.0-SNAPSHOT.jar"]
    assert len(starters) == 1
    if minimal:
        assert not any(part in str(p) for p in jars for part in
                       ("/caffeine/", "/resilience4j-", "/io/opentelemetry/", "/reactive-http-client-test/"))
    (out / "artifact-sha256.txt").write_text("".join(
        f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p}\n" for p in jars))


(out / "source-commit.txt").write_bytes(subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT))
(out / "source.patch").write_bytes(subprocess.check_output(["git", "diff", "HEAD"], cwd=ROOT))
(out / "source-status.txt").write_bytes(subprocess.check_output(["git", "status", "--short"], cwd=ROOT))
shutil.copy2(__file__, out / "runner.py")
(out / "started-at.txt").write_text(datetime.now(timezone.utc).isoformat() + "\n")
(out / "provenance.txt").write_text(
    f"Current reactor installed artifacts, NOT Central consumption.\nRepository: {args.repository.resolve()}\n")
run(["java", "-version"])
run(["mvn", "-version"])
if args.stage == "install":
    run(maven + ["-DskipTests", "-Dmaven.javadoc.skip=true", "install"])
elif args.stage in ("mock", "docs", "aot-tests", "aot-tests41"):
    module = "reactive-http-client-test" if args.stage == "mock" else "reactive-http-client-starter"
    tests = ("MockReactiveHttpClientTest,Boot4MockReactiveHttpClientTest,MockCacheWorkParityTest,MockInboundContextParityTest"
             if args.stage == "mock" else
             "AotPropertiesSelectionContractTest,AotMetadataSelectionContractTest,ReactiveHttpClientAotSmokeTest,EffectiveSelectionAotReviewTest,V33CrossPathContractTest")
    if args.stage == "docs":
        tests = "DocumentationReleaseArtifactTest"
    row = ["-Dspring-boot.version=4.1.0"] if args.stage == "aot-tests41" else []
    run(maven + row + ["-f", str(ROOT / module / "pom.xml"), "clean", "test", f"-Dtest={tests}",
                 "-DargLine=-XX:+DisableExplicitGC"])
    reports(ROOT / module / "target/surefire-reports", "reports")
else:
    boot = "4.1.0" if args.stage.endswith("41") else "4.0.0"
    minimal = args.stage.startswith("minimal")
    jvm = args.stage.startswith("jvm")
    source = ".github/native-smoke" if jvm else (
        ".github/boot4-cache-disabled-consumer" if minimal else ".github/boot4-consumer")
    fixture = overlay(source, boot)
    command = maven + ["-f", str(fixture / "pom.xml"), "-Dreactive-http-client.version=4.5.0-SNAPSHOT"]
    if not jvm:
        command += ["-Dconsumer.v31.parity=true"]
        if not minimal:
            command += ["-Dconsumer.v26.observability=true", "-Dconsumer.v27.parity=true",
                        "-Dconsumer.v28.parity=true", "-Dconsumer.v29.parity=true",
                        "-Dconsumer.v30.parity=true", "-Dconsumer.v32.extensions=true"]
    run(command + ["clean", "package" if jvm else "test"])
    reports(fixture / "target/surefire-reports", "reports")
    artifacts(command, boot, minimal)
    if jvm:
        jar = fixture / "target/reactive-http-client-native-smoke-0.0.1-SNAPSHOT.jar"
        run(["java", "-jar", str(jar)])
        run(command + ["spring-boot:process-aot", "package", "-DskipTests"])
        run(["java", "-Dspring.aot.enabled=true", "-jar", str(jar)])
print(f"Evidence passed: {out}")
