"""Exercise the real local adapter protocol with a deterministic provider fixture."""
from pathlib import Path
import subprocess,tempfile,shutil,urllib.request,urllib.error,json,os
root=Path(__file__).resolve().parents[1]
node=os.environ.get('NCM_NODE') or shutil.which('node')
if not node: raise RuntimeError('Install Node.js or set NCM_NODE to its executable')
(root/'build').mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(dir=root/'build',prefix='resolver-test-') as temp:
    path=Path(temp); (path/'src/provider').mkdir(parents=True)
    shutil.copy(root/'runtime/resolver.js',path/'resolver.js')
    (path/'src/provider/match.js').write_text("module.exports=async(id,sources)=>{if(sources[0]==='qq'&&process.env.QQ_COOKIE!=='test-cookie')throw Error('Cookie not forwarded');return {url:'https://example.com/audio.mp3',br:192000,size:1234,source:sources[0]};};")
    (path/'src/provider/insure.js').write_text('module.exports={};')
    token='a'*72;(path/'auth').write_text(json.dumps({'token':token}))
    process=subprocess.Popen([str(node),'resolver.js','auth'],cwd=path,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    try:
        ready=process.stdout.readline().strip();assert ready.startswith('READY '),ready
        base='http://127.0.0.1:'+ready.split()[1]
        def call(endpoint,auth=token):
            request=urllib.request.Request(base+endpoint,headers={'Authorization':'Bearer '+auth})
            try:
                with urllib.request.urlopen(request,timeout=5) as r:return r.status,json.load(r)
            except urllib.error.HTTPError as e:return e.code,json.load(e)
        assert call('/health')[1]['ok']
        assert call('/health','invalid')[0]==403
        assert call('/resolve?id=http://example.com')[0]==400
        assert call('/arbitrary')[0]==400
        status,data=call('/resolve?id=347230');assert status==200 and data['source']=='kuwo' and data['size']==1234
        assert call('/resolve?id=347230')[1]==data
        assert len(data['md5'])==32
        print('PASS: local authentication, health, ID validation, route scope, matching response, cache')
    finally:
        process.terminate();process.wait(timeout=5)
    (path/'auth').write_text(json.dumps({'token':token,'source':'qq','qqCookie':'test-cookie'}))
    process=subprocess.Popen([str(node),'resolver.js','auth'],cwd=path,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    try:
        ready=process.stdout.readline().strip();assert ready.startswith('READY '),ready
        base='http://127.0.0.1:'+ready.split()[1]
        assert call('/health')[1]['sources']==['qq']
        assert call('/resolve?id=347230')[1]['source']=='qq'
        assert 'test-cookie' not in json.dumps(call('/health')[1])
        print('PASS: QQ-only selection, Cookie passed before provider initialization, no Cookie in health response')
    finally:
        process.terminate();process.wait(timeout=5)
