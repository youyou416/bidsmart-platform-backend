#!/usr/bin/env python3
"""Verify application and the Java 11 selected entries of runtime dependency jars."""
import os
from pathlib import Path
import struct
from xml.etree import ElementTree as ET
from zipfile import ZipFile

root = Path(__file__).resolve().parent.parent
namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
pom = ET.parse(root / 'target/effective-pom.xml').getroot()
assert pom.findtext('m:parent/m:version', namespaces=namespace) == '2.5.3', 'Unexpected Boot baseline'
plugins = pom.findall('m:build/m:plugins/m:plugin', namespace)
assert any(p.findtext('m:artifactId', namespaces=namespace) == 'maven-compiler-plugin'
           and p.findtext('m:version', namespaces=namespace) == '3.8.1' for p in plugins)

def major(data):
    assert data[:4] == bytes.fromhex('cafebabe'), 'Invalid class header'
    return struct.unpack('>H', data[6:8])[0]

classes = list((root / 'target/classes').rglob('*.class'))
assert classes, 'Application classes missing'
for path in classes:
    assert major(path.read_bytes()) == 55, 'Application must be Java 11: ' + str(path)

jars = (root / 'target/dependency-classpath.txt').read_text().strip().split(os.pathsep)
checked = 0
for jar in jars:
    with ZipFile(jar) as archive:
        selected = {}
        multi_release = (b'multi-release: true' in archive.read('META-INF/MANIFEST.MF').lower()
                         if 'META-INF/MANIFEST.MF' in archive.namelist() else False)
        for name in archive.namelist():
            if not name.endswith('.class'):
                continue
            version = 0
            classname = name
            if name.startswith('META-INF/versions/'):
                pieces = name.split('/', 3)
                if not multi_release or len(pieces) != 4:
                    continue
                version = int(pieces[2])
                classname = pieces[3]
                if version > 11:
                    continue
            if classname not in selected or version > selected[classname][0]:
                selected[classname] = (version, name)
        for _, name in selected.values():
            assert major(archive.read(name)) <= 55, 'Requires newer Java: ' + jar + ':' + name
            checked += 1
print(f'PASS: Boot 2.5.3, compiler 3.8.1; {len(classes)} application classes major 55; '
      f'{len(jars)} runtime jars / {checked} selected dependency classes compatible with Java 11.')
