"""Build and export a committed WienScore revision, without local build inputs.

Requires Python 3, Git and Docker (Linux containers). Run from the repository root.
Creates a new output directory; refuses dirty source or an existing destination.
"""
import argparse
from datetime import datetime, timezone
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(command("git", "rev-parse", "--show-toplevel")).resolve()
    if Path.cwd().resolve() != root:
        raise SystemExit("Run this script from the repository root.")
    if command("git", "status", "--porcelain"):
        raise SystemExit("Commit or otherwise resolve working changes before exporting.")
    revision = command("git", "rev-parse", "HEAD")
    tag = "wienscore:" + revision[:12]
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    source = output / "wienscore-source.zip"
    subprocess.run(["git", "archive", "--format=zip", "--output", str(source), revision], check=True)
    checkout = output / "build-source"
    with zipfile.ZipFile(source) as archive:
        archive.extractall(checkout)
    # No layer-cache reuse: tests and builds execute from committed files only.
    subprocess.run(["docker", "build", "--no-cache", "--label",
                    "org.opencontainers.image.revision=" + revision,
                    "-t", tag, str(checkout)], check=True)
    image = json.loads(command("docker", "image", "inspect", tag))[0]
    image_file = output / "wienscore-image.tar.gz"
    with image_file.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as compressed:
            with subprocess.Popen(["docker", "save", tag], stdout=subprocess.PIPE) as process:
                shutil.copyfileobj(process.stdout, compressed)
                if process.wait() != 0:
                    raise RuntimeError("Docker image export failed.")
    (output / "compose.yaml").write_text(f'''services:
  wienscore:
    image: {tag}
    pull_policy: never
    ports:
      - "127.0.0.1:${{WIENSCORE_PORT:-8081}}:8081"
    volumes:
      - dataset-cache:/app/data-cache
    restart: unless-stopped
volumes:
  dataset-cache:
''', encoding="utf-8")
    shutil.copy2(root / "scripts" / "smoke-test.ps1", output / "smoke-test.ps1")
    (output / "START-HERE.md").write_text(f'''# WienScore Docker handover candidate

Revision: `{revision}`. Platform: `{image['Os']}/{image['Architecture']}`.
Install Docker Engine/Desktop with Linux containers and Compose. Allow HTTPS to
`data.wien.gv.at`. No Java, Node, Python, credentials or source build is needed to run.
This candidate contains the software; diary, presentation and demo video belong
in the later full submission package. This is not proof of lecturer acceptance.

From this folder:

```powershell
docker load --input wienscore-image.tar.gz
docker compose -p wienscore-handover up -d
```

Open http://localhost:8081. Try `Hoher Markt 1, 1010 Wien`, then select Senior.
Health: http://localhost:8081/actuator/health. A healthy process does not guarantee
that the external sources work. The first score downloads its datasets.
Run `./smoke-test.ps1` in PowerShell for both-profile live checks.
If port 8081 is occupied, set `$env:WIENSCORE_PORT = '8082'` before starting and
open http://localhost:8082; use `./smoke-test.ps1 -BaseUrl http://localhost:8082`.

```powershell
docker compose -p wienscore-handover logs --tail=100
docker compose -p wienscore-handover down
```

Stopping preserves cached data. Addresses still require a live source lookup.
No grade is returned for ambiguous locations or unusable mandatory datasets.
The source ZIP includes the application, tests and README with startup instructions.
The model is a draft amenity-access estimate, not a validated quality-of-life rating.
See `manifest.json` for image identity, revision, sizes and file SHA-256 values.
Compare using `Get-FileHash -Algorithm SHA256 <filename>` before loading.
The source ZIP has the committed tree; it does not include Git history. Provide
the accessible repository link separately during submission preparation.
''', encoding="utf-8")
    files = {}
    for file in sorted(output.iterdir()):
        if file.is_file():
            with file.open("rb") as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            files[file.name] = {"bytes": file.stat().st_size, "sha256": digest}
    manifest = {"createdAt": datetime.now(timezone.utc).isoformat(),
                "gitRevision": revision, "imageTag": tag, "imageId": image["Id"],
                "platform": image["Os"] + "/" + image["Architecture"],
                "runtimeUser": image["Config"]["User"], "files": files,
                "softwareBytes": sum(f["bytes"] for f in files.values()),
                "submissionLimitBytes": 500_000_000,
                "note": "Candidate only. Add academic artifacts and check final ZIP size separately."}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, indent=2))
    print("Copy only manifest-listed files plus manifest.json into a handover folder, not build-source.")


if __name__ == "__main__":
    main()
