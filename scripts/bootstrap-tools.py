#!/usr/bin/env python3
"""Install fixed Linux x64 toolchains using publisher checksums and verified TLS."""
import hashlib
from pathlib import Path
import shutil
import sys
import tarfile
import urllib.request

root = Path(sys.argv[1] if len(sys.argv) > 1 else str(Path.home() / '.bidsmart-toolchains'))
root.mkdir(parents=True, exist_ok=True)
cache = root / 'downloads'
cache.mkdir(exist_ok=True)

def fetch(url):
    with urllib.request.urlopen(url, timeout=120) as response:
        return response.read()

def install(name, url, checksum_url, algorithm, expected_file=None):
    archive = cache / url.rsplit('/', 1)[1]
    sums = fetch(checksum_url).decode('ascii')
    if expected_file:
        digest = next(line.split()[0] for line in sums.splitlines() if line.split()[-1] == expected_file)
    else:
        digest = sums.split()[0]
    if not archive.exists() or hashlib.new(algorithm, archive.read_bytes()).hexdigest() != digest:
        archive.write_bytes(fetch(url))
    if hashlib.new(algorithm, archive.read_bytes()).hexdigest() != digest:
        raise RuntimeError('Publisher checksum mismatch: ' + name)
    dest = root / name
    if not dest.exists():
        staging = root / (name + '-extracting')
        if staging.exists():
            shutil.rmtree(staging)
        staging.mkdir()
        with tarfile.open(archive) as tar:
            for member in tar.getmembers():
                resolved = (staging / member.name).resolve()
                if not resolved.is_relative_to(staging.resolve()):
                    raise RuntimeError('Unsafe archive path')
            tar.extractall(staging, filter='data')
        extracted = list(staging.iterdir())
        if len(extracted) != 1:
            raise RuntimeError('Unexpected archive layout')
        extracted[0].rename(dest)
        staging.rmdir()
    print(name + ': checksum verified, installed at ' + str(dest), flush=True)

install('jdk-11', 'https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.32.1%2B1/OpenJDK11U-jdk_x64_linux_hotspot_11.0.32.1_1.tar.gz', 'https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.32.1%2B1/OpenJDK11U-jdk_x64_linux_hotspot_11.0.32.1_1.tar.gz.sha256.txt', 'sha256')
maven = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.6.1/apache-maven-3.6.1-bin.tar.gz'
install('maven-3.6.1', maven, 'https://archive.apache.org/dist/maven/maven-3/3.6.1/binaries/apache-maven-3.6.1-bin.tar.gz.sha512', 'sha512')
install('node-20', 'https://nodejs.org/dist/v20.19.5/node-v20.19.5-linux-x64.tar.xz', 'https://nodejs.org/dist/v20.19.5/SHASUMS256.txt', 'sha256', 'node-v20.19.5-linux-x64.tar.xz')
print('Activate with JAVA_HOME=<root>/jdk-11 and prepend <root>/jdk-11/bin:<root>/maven-3.6.1/bin:<root>/node-20/bin to PATH.')
