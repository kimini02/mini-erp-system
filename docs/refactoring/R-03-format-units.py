#!/usr/bin/env python3
"""Read-only javac unit/short-line measurement; JDK + Python standard library only."""
import argparse,base64,json,os,re,subprocess,tempfile
from pathlib import Path
SCANNER=r'''
import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
class Units {
 public static void main(String[] args) throws Exception {
  Path root=Path.of(args[0]); var compiler=ToolProvider.getSystemJavaCompiler();
  try(var fm=compiler.getStandardFileManager(null,null,StandardCharsets.UTF_8)) {
   List<Path> paths=new ArrayList<>();
   for(String part:List.of("main","test")) try(var s=Files.walk(root.resolve("backend/src/"+part+"/java"))) {
    paths.addAll(s.filter(p->p.toString().endsWith(".java")).sorted().toList());
   }
   var task=(JavacTask)compiler.getTask(null,fm,null,List.of("-proc:none"),null,fm.getJavaFileObjectsFromPaths(paths));
   var positions=Trees.instance(task).getSourcePositions();
   for(var unit:task.parse()) {
    String source=unit.getSourceFile().getCharContent(true).toString();
    new TreePathScanner<Void,Void>() {
     long start(Tree t){return positions.getStartPosition(unit,t);}
     long end(Tree t){return positions.getEndPosition(unit,t);}
     void emit(Tree t,long a,long b){
      if(a<0||b<a)return;
      String slice=source.substring((int)a,(int)b);
      if(!slice.contains("\n"))return;
      boolean[] excluded = {false};
      new TreeScanner<Void,Void>() {
       boolean inside(Tree node){return start(node)>=a && end(node)<=b;}
       @Override public Void visitLambdaExpression(LambdaExpressionTree node,Void u){
        if(inside(node) && node.getBodyKind()==LambdaExpressionTree.BodyKind.STATEMENT)excluded[0]=true;
        return super.visitLambdaExpression(node,u);
       }
       @Override public Void visitSwitchExpression(SwitchExpressionTree node,Void u){
        if(inside(node))excluded[0]=true;return super.visitSwitchExpression(node,u);
       }
       @Override public Void visitNewClass(NewClassTree node,Void u){
        if(inside(node) && node.getClassBody()!=null)excluded[0]=true;return super.visitNewClass(node,u);
       }
      }.scan(t,null);
      if(excluded[0])return;
      System.out.println(root.relativize(Path.of(unit.getSourceFile().toUri()))+"\t"+t.getKind()+"\t"+a+"\t"+b);
     }
     long after(ModifiersTree m,long fallback){return m.getAnnotations().isEmpty()?fallback:end(m.getAnnotations().get(m.getAnnotations().size()-1));}
     void header(Tree t,StatementTree body){if(body.getKind()==Tree.Kind.BLOCK)emit(t,start(t),start(body)+1);}
     @Override public Void visitExpressionStatement(ExpressionStatementTree t,Void u){
      Tree parent=getCurrentPath().getParentPath().getLeaf();
      if(!(parent instanceof ForLoopTree))emit(t,start(t),end(t));return super.visitExpressionStatement(t,u);
     }
     @Override public Void visitReturn(ReturnTree t,Void u){emit(t,start(t),end(t));return super.visitReturn(t,u);}
     @Override public Void visitThrow(ThrowTree t,Void u){emit(t,start(t),end(t));return super.visitThrow(t,u);}
     @Override public Void visitVariable(VariableTree t,Void u){
      Tree parent=getCurrentPath().getParentPath().getLeaf();
      if(parent instanceof BlockTree||parent instanceof ClassTree)emit(t,after(t.getModifiers(),start(t)),end(t));
      return super.visitVariable(t,u);
     }
     @Override public Void visitIf(IfTree t,Void u){header(t,t.getThenStatement());return super.visitIf(t,u);}
     @Override public Void visitWhileLoop(WhileLoopTree t,Void u){header(t,t.getStatement());return super.visitWhileLoop(t,u);}
     @Override public Void visitForLoop(ForLoopTree t,Void u){header(t,t.getStatement());return super.visitForLoop(t,u);}
     @Override public Void visitEnhancedForLoop(EnhancedForLoopTree t,Void u){header(t,t.getStatement());return super.visitEnhancedForLoop(t,u);}
     @Override public Void visitMethod(MethodTree t,Void u){emit(t,after(t.getModifiers(),start(t)),t.getBody()==null?end(t):start(t.getBody())+1);return super.visitMethod(t,u);}
     @Override public Void visitAnnotation(AnnotationTree t,Void u){emit(t,start(t),end(t));return super.visitAnnotation(t,u);}
    }.scan(unit,null);
   }
  }
 }
}
'''
def measure(root):
 java=str(Path(os.environ['JAVA_HOME'])/'bin/java')
 with tempfile.TemporaryDirectory(prefix='r03-units-') as td:
  p=Path(td)/'Units.java';p.write_text(SCANNER)
  result=subprocess.run([java,str(p),str(root)],capture_output=True,text=True,check=True)
 out={'main':[],'test':[]}
 for line in result.stdout.splitlines():
  file,kind,a,b=line.split('\t');s=(root/file).read_text();a,b=int(a),int(b)
  while a<b and s[a].isspace():a+=1
  raw=s[a:b]
  # Exclusions are conservative: comments/text blocks, block lambdas, switch expressions and anonymous classes.
  # A literal/comment-aware token scan avoids treating URL strings as comments.
  import importlib.util
  if 'lex' not in locals():
   spec=importlib.util.spec_from_file_location('lex',root/'docs/refactoring/R-02-format-measure.py');lex=importlib.util.module_from_spec(spec);spec.loader.exec_module(lex)
  tokens=lex.tokenize(raw)
  if any(t[0]=='text' for t in tokens):continue
  gaps=''.join(raw[tokens[i-1][3] if i else 0:t[2]] for i,t in enumerate(tokens))
  if '//' in gaps or '/*' in gaps:continue
  ts=[t[1] for t in tokens]
  if '\n' not in raw:continue
  indent=len(s[s.rfind('\n',0,a)+1:a])
  compact=re.sub(r'[ \t]*\n\s*',' ',raw).replace('( ','(').replace(' )',')').replace(' .','.')
  if indent+len(compact)<=120:
   part='test' if '/test/' in file else 'main'
   out[part].append({'file':file,'line':s.count('\n',0,a)+1,'kind':kind,'length':indent+len(compact),'start':a,'end':b,'joined':' '*indent+compact})
 return {k:{'count':len(v),'locations':v} for k,v in out.items()}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);p.add_argument('--output',type=Path);a=p.parse_args();r=measure(a.root.resolve());text=json.dumps(r,ensure_ascii=False,indent=2)+'\n'
 if a.output:a.output.write_text(text)
 print(text)
if __name__=='__main__':main()
