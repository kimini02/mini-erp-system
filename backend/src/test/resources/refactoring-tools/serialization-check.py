#!/usr/bin/env python3
"""Run the actual writer with identical input in five fresh JVMs; no DB or application access."""
from pathlib import Path
import hashlib, os, subprocess, sys, tempfile
classpath=Path(sys.argv[1]).read_text().strip()
java_home=Path(os.environ['JAVA_HOME'])
source='''package com.minierp.backend.support.refactoring;
public final class SerializationCheck {
 public static void main(String[] args) throws Exception {
  System.setProperty("refactoring.record", args[0]);
  SafetyNetRun.begin("SerializationCheck#fixed()");
  SafetyNetRun.sql("SELECT database()", 1);
  SafetyNetRun.sql("SELECT 1", 2);
  SafetyNetRun.write();
 }
}
'''
with tempfile.TemporaryDirectory(prefix='r01-serialization-') as folder:
 root=Path(folder);code=root/'SerializationCheck.java';code.write_text(source)
 subprocess.run([str(java_home/'bin/javac'),'-cp',classpath,'-d',str(root),str(code)],check=True)
 samples=[]
 for index in range(5):
  output=root/str(index)
  subprocess.run([str(java_home/'bin/java'),'-cp',str(root)+os.pathsep+classpath,
   'com.minierp.backend.support.refactoring.SerializationCheck',str(output)],check=True,capture_output=True,text=True)
  sample={name:hashlib.sha256((output/name).read_bytes()).hexdigest()
   for name in ['sql.json','responses.json','masked-fields.txt']}
  samples.append(sample)
 assert all(sample==samples[0] for sample in samples),samples
 print('SERIALIZATION_CHECK fresh_jvms=5 byte_identical=5/5 sha256='+str(samples[0]))
