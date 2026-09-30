# Docker handover

Candidate **4d905e5** was exported and checked on 17 September 2026. Its software
files total about **119 MB**, excluding academic artifacts. The exported image
loaded and passed a live startup check with a newly created cache on this machine.
See [acceptance evidence](ACCEPTANCE.md) and the
[integrity manifest](evidence/sprint3-handover-manifest.json).

The repository supports source builds with `docker compose up --build -d`.
Export a loadable image and matching source from a clean committed revision:

```powershell
python scripts/export-handover.py --output backend/target/handover-candidate
```

The maintainer needs Python 3, Git, Docker with Linux containers, internet and
build space. The destination must not exist. The script refuses a dirty tree,
archives Git HEAD, builds the isolated tree without layer-cache reuse, runs the
Dockerfile's tests/builds, and writes:

- `wienscore-image.tar.gz`: compressed Docker image, loadable directly.
- `wienscore-source.zip`: committed source without Git history or ignored files.
- `compose.yaml`, `smoke-test.ps1`, `START-HERE.md`: build-free startup and checks.
- `manifest.json`: revision, image ID/tag, platform, runtime user, sizes and SHA-256.

The temporary `build-source` stays in local build output. Copy only manifest-listed
files plus the manifest into a NAS handover deliverable. Do not copy generated
development files into synced Documents. This is a software candidate, not the
complete course submission.

## Verify the exported image

From the candidate folder, load it and use a separate Compose project/fresh cache:

```powershell
docker load --input wienscore-image.tar.gz
$env:WIENSCORE_PORT = '8082'
docker compose -p wienscore-handover-check up -d
./smoke-test.ps1 -BaseUrl http://localhost:8082
docker compose -p wienscore-handover-check down
```

`pull_policy: never` uses the loaded image. Runtime needs internet for addresses
and the first data download. No source, host JDK, Node or pre-populated cache is
mounted. Named cache data survive `down`. Inspect the running image ID and user
against the manifest. Same-machine isolated startup is useful evidence, but is
not a second-machine test. Record architecture; Linux amd64 is the target here.

## Submission gate

Keep identity and hashes with the final files. Later code changes require a new
export/check. Verify the combined ZIP stays below 500 MB after diary, workbook,
editable slides and the user-recorded demo are added. Accessible Git history,
formal review and actual effort remain separate items. The course wording does
not conclusively define the preferred Docker format; a loadable image plus source
and instructions is the documented interpretation.
