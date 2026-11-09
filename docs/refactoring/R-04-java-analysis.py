#!/usr/bin/env python3
"""Read-only javac attribution shared by R-04 audits. JDK and Python stdlib only.

Each compilation unit is attributed against its root's already compiled classes,
so Lombok-generated members in other units remain visible without annotation
processors. No application code is executed. Cache keys include all source bytes,
compiled class bytes, analyzer bytes and the dependency classpath.
"""
import argparse, hashlib, json, os, re, subprocess, tempfile
from pathlib import Path
JAVA = r'''
import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.lang.model.element.*;
import javax.tools.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
class R04Ast {
 static String json(Object x) {
  if(x==null)return "null";
  if(x instanceof Character c)return json(c.toString());
  if(x instanceof String s)return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t").replace("\b","\\b").replace("\f","\\f")+"\"";
  if(x instanceof Map<?,?> m)return "{"+String.join(",",m.entrySet().stream().map(e->json(e.getKey().toString())+":"+json(e.getValue())).toList())+"}";
  if(x instanceof Iterable<?> it){var a=new ArrayList<String>();for(Object v:it)a.add(json(v));return "["+String.join(",",a)+"]";}
  return x.toString();
 }
 static Map<String,Object> map(Object... pairs){var out=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
 static class Reader {
  CompilationUnitTree unit; Trees trees; String source; SourcePositions positions;
  Reader(CompilationUnitTree u,Trees t)throws Exception{unit=u;trees=t;source=u.getSourceFile().getCharContent(true).toString();positions=t.getSourcePositions();}
  Element element(Tree t){try{return trees.getElement(TreePath.getPath(unit,t));}catch(Exception e){return null;}}
  String type(Tree t){try{var x=trees.getTypeMirror(TreePath.getPath(unit,t));return x==null?"":x.toString();}catch(Exception e){return "";}}
  String owner(Element e){return e==null?"":e.getEnclosingElement().toString();}
  String signature(Element e){return e instanceof ExecutableElement x ? x.getSimpleName()+"("+String.join(",",x.getParameters().stream().map(p->p.asType().toString()).toList())+")":"";}
  Object nodes(Iterable<? extends Tree> nodes){var out=new ArrayList<Object>();for(Tree t:nodes){Object v=node(t);if(v!=null)out.add(v);}return out;}
  Map<String,Object> node(Tree t){
   if(t==null)return null;long start=positions.getStartPosition(unit,t),end=positions.getEndPosition(unit,t);if(start<0||end<start)return null;
   var r=map("kind",t.getKind().toString(),"start",start,"end",end,"line",unit.getLineMap().getLineNumber(start));
   if(t instanceof ExpressionTree||t instanceof VariableTree)r.put("type",type(t));
   if(t instanceof IdentifierTree v){var e=element(t);r.putAll(map("name",v.getName().toString(),"owner",owner(e),"symbolKind",e==null?"":e.getKind().toString()));}
   else if(t instanceof MemberSelectTree v){var e=element(t);r.putAll(map("name",v.getIdentifier().toString(),"receiver",node(v.getExpression()),"owner",owner(e),"symbolKind",e==null?"":e.getKind().toString()));}
   else if(t instanceof MethodInvocationTree v){var e=element(t);r.putAll(map("select",node(v.getMethodSelect()),"args",nodes(v.getArguments()),"owner",owner(e),"signature",signature(e)));}
   else if(t instanceof MemberReferenceTree v){var e=element(t);r.putAll(map("receiver",node(v.getQualifierExpression()),"name",v.getName().toString(),"owner",owner(e),"signature",signature(e)));}
   else if(t instanceof NewClassTree v)r.putAll(map("class",v.getIdentifier().toString(),"args",nodes(v.getArguments()),"body",node(v.getClassBody())));
   else if(t instanceof LiteralTree v)r.putAll(map("value",v.getValue(),"raw",source.substring((int)start,(int)end)));
   else if(t instanceof ParenthesizedTree v)r.put("expression",node(v.getExpression()));
   else if(t instanceof TypeCastTree v)r.putAll(map("cast",v.getType().toString(),"expression",node(v.getExpression())));
   else if(t instanceof ConditionalExpressionTree v)r.putAll(map("condition",node(v.getCondition()),"then",node(v.getTrueExpression()),"else",node(v.getFalseExpression())));
   else if(t instanceof BinaryTree v)r.putAll(map("left",node(v.getLeftOperand()),"right",node(v.getRightOperand())));
   else if(t instanceof UnaryTree v)r.put("expression",node(v.getExpression()));
   else if(t instanceof AssignmentTree v)r.putAll(map("left",node(v.getVariable()),"right",node(v.getExpression())));
   else if(t instanceof CompoundAssignmentTree v)r.putAll(map("left",node(v.getVariable()),"right",node(v.getExpression())));
   else if(t instanceof ArrayAccessTree v)r.putAll(map("expression",node(v.getExpression()),"index",node(v.getIndex())));
   else if(t instanceof NewArrayTree v)r.putAll(map("dimensions",nodes(v.getDimensions()),"values",v.getInitializers()==null?null:nodes(v.getInitializers())));
   else if(t instanceof LambdaExpressionTree v)r.putAll(map("parameters",nodes(v.getParameters()),"body",node(v.getBody())));
   else if(t instanceof VariableTree v)r.putAll(map("name",v.getName().toString(),"declaredType",v.getType()==null?"":v.getType().toString(),"modifiers",v.getModifiers().getFlags().stream().map(Object::toString).sorted().toList(),"annotations",nodes(v.getModifiers().getAnnotations()),"initializer",node(v.getInitializer())));
   else if(t instanceof MethodTree v){var e=element(t);r.putAll(map("name",v.getName().toString(),"owner",owner(e),"signature",signature(e),"returns",v.getReturnType()==null?"":v.getReturnType().toString(),"modifiers",v.getModifiers().getFlags().stream().map(Object::toString).sorted().toList(),"annotations",nodes(v.getModifiers().getAnnotations()),"parameters",nodes(v.getParameters()),"body",node(v.getBody())));}
   else if(t instanceof ClassTree v){var e=element(t);r.putAll(map("name",v.getSimpleName().toString(),"qualified",e==null?unit.getPackageName()+"."+v.getSimpleName():e.toString(),"members",nodes(v.getMembers()),"annotations",nodes(v.getModifiers().getAnnotations())));}
   else if(t instanceof BlockTree v)r.put("statements",nodes(v.getStatements()));
   else if(t instanceof ExpressionStatementTree v)r.put("expression",node(v.getExpression()));
   else if(t instanceof ReturnTree v)r.put("expression",node(v.getExpression()));
   else if(t instanceof ThrowTree v)r.put("expression",node(v.getExpression()));
   else if(t instanceof IfTree v)r.putAll(map("condition",node(v.getCondition()),"then",node(v.getThenStatement()),"else",node(v.getElseStatement())));
   else if(t instanceof ForLoopTree v)r.putAll(map("init",nodes(v.getInitializer()),"condition",node(v.getCondition()),"update",nodes(v.getUpdate()),"body",node(v.getStatement())));
   else if(t instanceof EnhancedForLoopTree v)r.putAll(map("variable",node(v.getVariable()),"expression",node(v.getExpression()),"body",node(v.getStatement())));
   else if(t instanceof WhileLoopTree v)r.putAll(map("condition",node(v.getCondition()),"body",node(v.getStatement())));
   else if(t instanceof DoWhileLoopTree v)r.putAll(map("body",node(v.getStatement()),"condition",node(v.getCondition())));
   else if(t instanceof TryTree v)r.putAll(map("resources",nodes(v.getResources()),"body",node(v.getBlock()),"catches",nodes(v.getCatches()),"finally",node(v.getFinallyBlock())));
   else if(t instanceof CatchTree v)r.putAll(map("parameter",node(v.getParameter()),"body",node(v.getBlock())));
   else if(t instanceof SwitchTree v)r.putAll(map("expression",node(v.getExpression()),"cases",nodes(v.getCases())));
   else if(t instanceof SwitchExpressionTree v)r.putAll(map("expression",node(v.getExpression()),"cases",nodes(v.getCases())));
   else if(t instanceof CaseTree v)r.putAll(map("labels",nodes(v.getExpressions()),"statements",v.getStatements()==null?null:nodes(v.getStatements()),"body",node(v.getBody())));
   else if(t instanceof SynchronizedTree v)r.putAll(map("expression",node(v.getExpression()),"body",node(v.getBlock())));
   else if(t instanceof LabeledStatementTree v)r.put("body",node(v.getStatement()));
   else if(t instanceof YieldTree v)r.put("expression",node(v.getValue()));
   else if(t instanceof AnnotationTree v)r.putAll(map("name",v.getAnnotationType().toString(),"raw",source.substring((int)start,(int)end),"args",nodes(v.getArguments())));
   else if(t instanceof InstanceOfTree v)r.putAll(map("expression",node(v.getExpression()),"pattern",node(v.getPattern())));
   else if(t instanceof BindingPatternTree v)r.put("variable",node(v.getVariable()));
   return r;
  }
 }
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]);String cp=args[1];var compiler=ToolProvider.getSystemJavaCompiler();
  for(int i=2;i<args.length;i++)try(var fm=compiler.getStandardFileManager(null,null,StandardCharsets.UTF_8)){
   var errors=new ArrayList<String>();var task=(JavacTask)compiler.getTask(null,fm,d->{if(d.getKind()==Diagnostic.Kind.ERROR)errors.add(d.getMessage(Locale.ROOT));},List.of("-proc:none","-classpath",cp,"-sourcepath",""),null,fm.getJavaFileObjects(root.resolve(args[i])));
   var units=new ArrayList<CompilationUnitTree>();task.parse().forEach(units::add);task.analyze();var trees=Trees.instance(task);
   for(var unit:units){var r=new Reader(unit,trees);var ids=new ArrayList<Object>();
    new TreePathScanner<Void,Void>(){
     @Override public Void visitImport(ImportTree t,Void v){return null;}
     @Override public Void visitIdentifier(IdentifierTree t,Void v){var e=trees.getElement(getCurrentPath());if(e!=null)ids.add(map("name",t.getName().toString(),"kind",e.getKind().toString(),"owner",r.owner(e),"qualified",e.toString(),"static",e.getModifiers().contains(Modifier.STATIC)));return super.visitIdentifier(t,v);}
    }.scan(unit,null);
    var imports=unit.getImports().stream().map(x->map("name",x.getQualifiedIdentifier().toString(),"static",x.isStatic(),"line",unit.getLineMap().getLineNumber(trees.getSourcePositions().getStartPosition(unit,x)))).toList();
    System.out.println(json(map("file",args[i],"package",unit.getPackageName().toString(),"imports",imports,"identifiers",ids,"classes",r.nodes(unit.getTypeDecls()),"diagnostics",errors)));
   }
  }
 }
}
'''
def load_module(name):
 import importlib.util
 spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(name+'.py'))
 module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module

def analyze(root,classpath,files=None,cache=None):
 root=Path(root).resolve(); paths=sorted(files or [str(p.relative_to(root)) for p in (root/'backend/src/main/java').rglob('*.java')])
 cp=Path(classpath).read_text().strip() if isinstance(classpath,Path) else classpath
 deps=[p for p in cp.split(os.pathsep) if not re.search(r'/target/(?:test-classes|classes)$',p)]
 cp=os.pathsep.join([str(root/'backend/target/classes'),*deps])
 assert (root/'backend/target/classes').is_dir(),'Compile a separate copy first'
 fingerprint=hashlib.sha256(JAVA.encode()+cp.encode())
 for f in paths:fingerprint.update(f.encode());fingerprint.update((root/f).read_bytes())
 for p in sorted((root/'backend/target/classes').rglob('*.class')):fingerprint.update(p.read_bytes())
 key=fingerprint.hexdigest(); cached=Path(cache)/f'{key}.json' if cache else None
 if cached and cached.exists():return json.loads(cached.read_text())
 with tempfile.TemporaryDirectory(prefix='r04-javac-') as tmp:
  source=Path(tmp)/'R04Ast.java';source.write_text(JAVA)
  java=Path(os.environ['JAVA_HOME'])/'bin/java'
  result=subprocess.run([str(java),str(source),str(root),cp,*paths],text=True,capture_output=True)
  if result.returncode:raise RuntimeError(result.stderr)
  data=[json.loads(line) for line in result.stdout.splitlines()]
 if cached:cached.parent.mkdir(parents=True,exist_ok=True);cached.write_text(json.dumps(data,ensure_ascii=False)+'\n')
 return data

def walk(node):
 if isinstance(node,dict):
  if 'kind' in node:yield node
  for v in node.values():yield from walk(v)
 elif isinstance(node,list):
  for v in node:yield from walk(v)

def classes(units):
 for unit in units:
  for c in walk(unit['classes']):
   if c.get('kind') in ('CLASS','RECORD','ENUM','INTERFACE'):yield unit,c

def output(result,path):
 text=json.dumps(result,ensure_ascii=False,indent=2)+'\n'
 if path:Path(path).write_text(text)
 print(json.dumps({k:v for k,v in result.items() if not isinstance(v,(list,dict))},ensure_ascii=False))

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,required=True);p.add_argument('--classpath',type=Path,required=True);p.add_argument('--file',action='append');p.add_argument('--cache',type=Path);p.add_argument('--output',type=Path,required=True);a=p.parse_args();data=analyze(a.root,a.classpath,a.file,a.cache);a.output.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n');print('units',len(data))
