#!/usr/bin/env python3
"""Read-only SQL call pairing: raw tokens, map keys/value order and javac attributed types.

Roots must have compiled target/classes (compile copies outside the worktree if needed).
--classpath is the recording's classpath.txt; its project target directories are replaced
with each root's classes. No source, database or baseline files are modified. Unresolved
or indirect map/helper cases are explicit manual-review items, never automatic passes.
"""
import argparse,base64,collections,importlib.util,json,os,re,subprocess,tempfile
from pathlib import Path
SCANNER=r'''
import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
class Calls {
 static String enc(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]);String cp=args[1]; var compiler=ToolProvider.getSystemJavaCompiler();
  // Attribute one unit at a time: all other beans/DTOs use compiled Lombok accessors.
  for(int index=2;index<args.length;index++)try(var fm=compiler.getStandardFileManager(null,null,StandardCharsets.UTF_8)){
   Path file=root.resolve(args[index]); var errors=new ArrayList<String>();
   var task=(JavacTask)compiler.getTask(null,fm,d->{if(d.getKind()==Diagnostic.Kind.ERROR)errors.add(d.toString());},
     List.of("-proc:none","-classpath",cp,"-sourcepath",""),null,fm.getJavaFileObjects(file));
   var units=new ArrayList<CompilationUnitTree>();task.parse().forEach(units::add);task.analyze();
   var trees=Trees.instance(task);var positions=trees.getSourcePositions();
   for(var unit:units){
    String source=unit.getSourceFile().getCharContent(true).toString();
    new TreePathScanner<Void,Void>(){
     String method="<class>";
     String raw(Tree t){long a=positions.getStartPosition(unit,t),b=positions.getEndPosition(unit,t);return a<0||b<0?"":source.substring((int)a,(int)b);}
     String type(Tree t){try{return trees.getTypeMirror(TreePath.getPath(unit,t)).toString();}catch(Exception e){return "<unresolved>";}}
     TypeMirror standalone(Tree t){
      if(t instanceof ParenthesizedTree p)return standalone(p.getExpression());
      if(t instanceof ConditionalExpressionTree c){
       var a=standalone(c.getTrueExpression());var b=standalone(c.getFalseExpression());
       if(a.getKind()==TypeKind.NULL)return b;if(b.getKind()==TypeKind.NULL)return a;
       if(task.getTypes().isSameType(a,b))return a;
       if(!a.getKind().isPrimitive() && !b.getKind().isPrimitive()){
        if(task.getTypes().isAssignable(a,b))return b;if(task.getTypes().isAssignable(b,a))return a;
       }
      }
      return trees.getTypeMirror(TreePath.getPath(unit,t));
     }
     String valueType(Tree t){try{return standalone(t).toString();}catch(Exception e){return "<unresolved>";}}
     String referenceSupertypes(Tree t){
      try {
       var initial=standalone(t);if(initial.getKind().isPrimitive())return "";
       var names=new LinkedHashSet<String>();var queue=new ArrayDeque<TypeMirror>();queue.add(initial);
       while(!queue.isEmpty()){
        var next=queue.removeFirst();if(!names.add(next.toString()))continue;
        queue.addAll(task.getTypes().directSupertypes(next));
       }
       return String.join("\n",names);
      }catch(Exception e){return "<unresolved>";}
     }
     void output(String kind,Tree t,List<String> fields){long a=positions.getStartPosition(unit,t);if(a<0)return;
      System.out.println(kind+"\t"+args[index]+"\t"+unit.getLineMap().getLineNumber(a)+"\t"+a+"\t"+positions.getEndPosition(unit,t)+"\t"+enc(method)+"\t"+String.join("\t",fields.stream().map(Calls::enc).toList()));
     }
     @Override public Void visitMethod(MethodTree t,Void u){String prev=method;method=t.getName().toString();var fields=new ArrayList<String>();fields.add(type(t));
      for(var p:t.getParameters()){fields.add(p.getName().toString());fields.add(type(p));}output("M",t,fields);super.visitMethod(t,u);method=prev;return null;}
     @Override public Void visitMethodInvocation(MethodInvocationTree t,Void u){
      var fields=new ArrayList<String>();fields.add(raw(t.getMethodSelect()));fields.add(type(t));
      Element element=trees.getElement(getCurrentPath());fields.add(element==null?"<unresolved>":element.getEnclosingElement().toString());
      for(var a:t.getArguments()){fields.add(raw(a));fields.add(valueType(a));fields.add(referenceSupertypes(a));}output("C",t,fields);return super.visitMethodInvocation(t,u);
     }
    }.scan(unit,null);
   }
  }
 }
}
'''
def collect(root,classpath,files):
 if not (root/'backend/target/classes').is_dir():
  raise ValueError('Compile an isolated root first: '+str(root))
 deps=[x for x in classpath.split(os.pathsep) if not re.search(r'/target/(test-classes|classes)$',x)]
 cp=os.pathsep.join([str(root/'backend/target/classes'),*deps]);java=str(Path(os.environ['JAVA_HOME'])/'bin/java')
 # Source-file launcher compiles the analyzer, not project sources.
 with tempfile.TemporaryDirectory(prefix='r03-calls-') as td:
  p=Path(td)/'Calls.java';p.write_text(SCANNER.replace('Path file=root.resolve(args[index]);','final String relative=args[index]; Path file=root.resolve(relative);').replace('args[index]+"\\t"','relative+"\\t"'))
  r=subprocess.run([java,str(p),str(root),cp,*files],text=True,capture_output=True)
  if r.returncode:raise RuntimeError(r.stderr)
 records=[]
 for line in r.stdout.splitlines():
  kind,file,line,a,b,method,*values=line.split('\t');v=[base64.b64decode(x).decode() for x in values]
  row={'kind':kind,'file':file,'line':int(line),'start':int(a),'end':int(b),'method':base64.b64decode(method).decode()}
  if kind=='C':row.update(select=v[0],type=v[1],owner=v[2],args=[{'raw':v[i],'type':v[i+1],'referenceSupertypes':v[i+2].splitlines()} for i in range(3,len(v),3)])
  else:row.update(type=v[0],params=[{'name':v[i],'type':v[i+1]} for i in range(1,len(v),2)])
  records.append(row)
 return records

def check(before,after,cp,files):
 spec=importlib.util.spec_from_file_location('lex',after/'docs/refactoring/R-02-format-measure.py');lex=importlib.util.module_from_spec(spec);spec.loader.exec_module(lex)
 def tokens(s):return tuple(t[1] for t in lex.tokenize(s))
 repos=sorted(str(p.relative_to(after)) for d in {f.split('/domain/')[1].split('/')[0] for f in files} for p in (after/f'backend/src/main/java/com/minierp/backend/domain/{d}/repository').glob('*.java'))
 old=collect(before,cp,files);new=collect(after,cp,files+repos)
 sql=lambda r:r['kind']=='C' and r['select'].split('.')[-1] in ['one','rows','count','update','insert','queryForList'] and r['select'].split('.')[0] in ['db','jdbc']
 originals=[r for r in old if sql(r)];targets=[r for r in new if sql(r) and r['file'] in repos]
 def params(call,records):
  if len(call['args'])<2:return [],[]
  raw=call['args'][1]['raw']
  if raw=='Map.of()':return [],[]
  nested=[r for r in records if r['kind']=='C' and r['file']==call['file'] and call['start']<r['start']<r['end']<call['end'] and r['select']=='map' and tokens((before if records is old else after).joinpath(r['file']).read_text()[r['start']:r['end']])==tokens(raw)]
  if len(nested)!=1:return None,None
  a=nested[0]['args'];return [x['raw'] for x in a[::2]],a[1::2]
 def compatible(value,target):
  source=value['type']
  if source==target:return True
  primitives={'byte','short','char','int','long','float','double','boolean'}
  if source in primitives or target in primitives:return False
  # javac supplies the standalone expression's reference supertypes. No primitive/boxing conversion is allowed.
  return source == '<nulltype>' or target in value.get('referenceSupertypes', [])
 result=[];failures=[];manual=[];put_results=[];put_manual=[]
 for o in originals:
  matching=[t for t in targets if tokens(t['args'][0]['raw'])==tokens(o['args'][0]['raw']) and t['select']==o['select']]
  pairs=[]
  for t in matching:
   cs=[c for c in new if c['kind']=='C' and c['file']==o['file'] and c['method']==o['method'] and c['owner'].endswith('.'+Path(t['file']).stem) and c['select'].endswith('.'+t['method'])]
   for c in cs:pairs.append((t,c))
  entry={'origin':o['file']+':'+str(o['line']),'sourceMethod':o['method'],'sql':o['args'][0]['raw']}
  ok,ov=params(o,old)
  # Multiple identical SQL calls can occur in the same service method. Their value expressions disambiguate.
  if len(pairs)>1 and ov is not None:
   precise=[(t,c) for t,c in pairs if [tokens(x['raw']) for x in c['args']]==[tokens(x['raw']) for x in ov]]
   if precise:pairs=precise
  if len(pairs)!=1:
   entry['manualReason']='unpaired/ambiguous SQL call; explicit SQL helper flow review required';entry['candidates']=[t['file']+'#'+t['method'] for t in matching];manual.append(entry);continue
  t,c=pairs[0];entry['target']=t['file']+'#'+t['method'];entry['returnType']=t['type'];entry['sqlTokensEqual']=True
  tk,tv=params(t,new);entry['mapKeysBefore']=ok;entry['mapKeysAfter']=tk
  if len(o['args'])>1 and re.fullmatch(r'\w+',o['args'][1]['raw']):
   variable=o['args'][1]['raw']
   puts=[r for r in old if r['kind']=='C' and r['file']==o['file'] and r['method']==o['method']
         and r['select']==variable+'.put' and r['start']<o['start'] and len(r['args'])==2]
   target_puts=[r for r in new if r['kind']=='C' and r['file']==t['file'] and r['method']==t['method']
                and r['select'].endswith('.put') and len(r['args'])==2]
   declarations=[m for m in new if m['kind']=='M' and m['file']==t['file'] and m['method']==t['method']]
   for put in puts:
    matches=[z for z in target_puts if tokens(z['args'][0]['raw'])==tokens(put['args'][0]['raw'])]
    pr={'origin':put['file']+':'+str(put['line']),'key':put['args'][0]['raw'],'sourceType':put['args'][1]['type'],'target':entry['target']}
    if len(matches)!=1 or len(declarations)!=1:
     pr['reason']='put flows through SQL assembly helper; inspect helper/caller';put_manual.append(pr);continue
    mp=matches[0];parameters=declarations[0]['params'];index=next((i for i,v in enumerate(parameters) if v['name']==mp['args'][1]['raw']),None)
    if index is None or index>=len(c['args']):
     pr['reason']='put value is not a direct query parameter';put_manual.append(pr);continue
    pr['parameterType']=parameters[index]['type'];pr['callerValue']=c['args'][index]['raw'];pr['sourceValue']=put['args'][1]['raw'];pr['typesCompatible']=compatible(put['args'][1],pr['parameterType'])
    pr['sameExpression']=tokens(pr['sourceValue'])==tokens(pr['callerValue'])
    # A local snapshot may replace a computed put value; report expression flow for manual validation.
    if not pr['sameExpression']:pr['manualExpression']='caller stores original expression in local value before query'
    put_results.append(pr)
    if not pr['typesCompatible']:failures.append({'origin':pr['origin'],'problems':['put value/parameter type mismatch'],**pr})

  methods=[m for m in new if m['kind']=='M' and m['file']==t['file'] and m['method']==t['method']]
  if ok is None or tk is None or len(methods)!=1:
   entry['manualReason']='indirect parameter map or overload';manual.append(entry);continue
  ps=methods[0]['params'];problems=[]
  if ok!=tk:problems.append('map key/order mismatch')
  if [tokens(x['raw']) for x in ov]!=[tokens(x['raw']) for x in c['args']]:problems.append('call argument expression/order mismatch')
  if [x['raw'] for x in tv]!=[x['name'] for x in ps]:problems.append('repository map value/parameter order mismatch')
  entry['originalValueTypes']=[x['type'] for x in ov];entry['parameterTypes']=[x['type'] for x in ps]
  if len(ov)!=len(ps) or any(not compatible(x,y['type']) for x,y in zip(ov,ps)):
   problems.append('static type mismatch (boxing/unboxing/primitive conversion prohibited)')
  if any('<' in x and 'unresolved' in x for x in entry['originalValueTypes']):problems.append('unresolved attributed type')
  entry['problems']=problems;result.append(entry)
  if problems:failures.append(entry)
 return {'originalSqlCalls':len(originals),'automaticallyPaired':len(result),'differences':len(failures),'manualReviewCount':len(manual),'results':result,'differencesDetail':failures,'manualReview':manual,'putChecks':put_results,'putManualReview':put_manual}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--before',type=Path,required=True);p.add_argument('--after',type=Path,required=True);p.add_argument('--classpath',type=Path,required=True);p.add_argument('--file',action='append',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();r=check(a.before.resolve(),a.after.resolve(),a.classpath.read_text().strip(),a.file);a.output.write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:v for k,v in r.items() if not isinstance(v,list)}));raise SystemExit(bool(r['differences']))
if __name__=='__main__':main()
