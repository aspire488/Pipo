const $=s=>document.querySelector(s);
const canvas=$('#scene'),ctx=canvas.getContext('2d');
const names=['Pipo','little idiot'];const places=[
 ['Room','🏠',true,'home base'],['Garden','🌿',true,'where Nib chases bugs'],['Park','🌳',true,'good for football'],['Hardware Shop','🔧',true,'Grumble knows him'],['Cricket Ground','🏏',false,'maybe tomorrow'],['Lake','🌊',false,'frogs have opinions'],['Hills','⛰️',false,'foggy at night'],['???','❔',false,'something is here']
];
const things=[['⚽','Football','left by the door'],['🧲','Odd magnet','bought by mistake'],['📷','Tiny camera','three blurry birds'],['🪛','Screwdriver','workbench'],['🍜','Noodles','Pipo says yes'],['🔩','Two-way screw','do not use'],['💡','Desk lamp','Nib likes staring at it'],['📝','Sketchbook','full of suspicious drawings']];
const defaultState={mood:'curious',activity:'looking around',speech:'...',x:.5,y:.67,targetX:.5,targetY:.67,nibX:.57,nibY:.72,location:'Room',sound:true,visited:[0,1,2,3],items:things.slice(0,5),journal:[
 ['Today','I was going to build a flying machine. Then I found a magnet. This is basically the same thing.'],
 ['Yesterday','Nib stole the screwdriver again. I know it was Nib because the screwdriver was under his bed. He looked away.'],
 ['A while ago','The mirror did the weird thing again. I did not like it. Nib did not like it more.']
]};
let state=JSON.parse(localStorage.getItem('pipo-web-state')||'null')||structuredClone(defaultState);
let last=performance.now(),t=0,moveTimer=0;

function save(){localStorage.setItem('pipo-web-state',JSON.stringify(state))}
function resize(){const r=canvas.getBoundingClientRect(),d=devicePixelRatio;canvas.width=r.width*d;canvas.height=r.height*d;ctx.setTransform(d,0,0,d,0,0)}
addEventListener('resize',resize);resize();

function roundRect(x,y,w,h,r){ctx.beginPath();ctx.roundRect(x,y,w,h,r);ctx.fill()}
function draw(){
 const w=canvas.clientWidth,h=canvas.clientHeight;
 ctx.clearRect(0,0,w,h);
 const night=new Date().getHours(); const dark=night<7||night>19;
 const g=ctx.createLinearGradient(0,0,0,h);g.addColorStop(0,dark?'#11192c':'#91b6c8');g.addColorStop(1,dark?'#263048':'#d9c9a8');ctx.fillStyle=g;ctx.fillRect(0,0,w,h);
 // window
 ctx.fillStyle=dark?'#15223c':'#b7d9e5';roundRect(w*.63,h*.11,w*.26,h*.27,10);ctx.strokeStyle='#e8e2d6';ctx.lineWidth=7;ctx.strokeRect(w*.63,h*.11,w*.26,h*.27);
 ctx.strokeStyle='#ffffff66';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(w*.76,h*.11);ctx.lineTo(w*.76,h*.38);ctx.moveTo(w*.63,h*.245);ctx.lineTo(w*.89,h*.245);ctx.stroke();
 // hills/window
 ctx.fillStyle=dark?'#26324b':'#71977e';ctx.beginPath();ctx.moveTo(w*.63,h*.38);ctx.lineTo(w*.7,h*.29);ctx.lineTo(w*.78,h*.35);ctx.lineTo(w*.84,h*.26);ctx.lineTo(w*.89,h*.35);ctx.lineTo(w*.89,h*.38);ctx.fill();
 // room floor
 ctx.fillStyle=dark?'#332c36':'#a78667';ctx.beginPath();ctx.moveTo(0,h*.58);ctx.lineTo(w,h*.5);ctx.lineTo(w,h);ctx.lineTo(0,h);ctx.fill();
 // rug
 ctx.fillStyle='#b8879c66';ctx.beginPath();ctx.ellipse(w*.43,h*.79,w*.29,h*.12,0,0,Math.PI*2);ctx.fill();
 // desk
 ctx.fillStyle='#553f38';roundRect(w*.08,h*.34,w*.38,h*.08,8);ctx.fillRect(w*.12,h*.41,w*.035,h*.25);ctx.fillRect(w*.4,h*.41,w*.035,h*.25);
 ctx.fillStyle='#d8b77a';roundRect(w*.14,h*.29,w*.12,h*.05,5);ctx.fillStyle='#77d6d9';roundRect(w*.18,h*.22,w*.07,h*.07,4);
 // bed
 ctx.fillStyle='#5c5878';roundRect(w*.63,h*.55,w*.28,h*.18,12);ctx.fillStyle='#b8b5cc';roundRect(w*.66,h*.57,w*.23,h*.1,9);
 // shelf
 ctx.fillStyle='#634b43';roundRect(w*.08,h*.47,w*.19,h*.035,5);ctx.fillStyle='#e5b27d';ctx.fillRect(w*.11,h*.42,10,25);ctx.fillStyle='#a9d1a4';ctx.fillRect(w*.16,h*.42,15,25);ctx.fillStyle='#d8a4a4';ctx.fillRect(w*.23,h*.42,12,25);
 // door
 ctx.fillStyle='#745b55';roundRect(w*.86,h*.28,w*.1,h*.3,8);ctx.fillStyle='#e6c77c';ctx.beginPath();ctx.arc(w*.94,h*.43,3,0,7);ctx.fill();
 // workbench
 ctx.fillStyle='#493a36';roundRect(w*.36,h*.48,w*.22,h*.055,7);ctx.fillStyle='#d3a36b';ctx.fillRect(w*.39,h*.535,7,h*.15);ctx.fillRect(w*.54,h*.535,7,h*.15);
 // pipo
 drawBot(state.x*w,state.y*h,1,dark);
 // nib
 drawNib(state.nibX*w,state.nibY*h);
}
function drawBot(x,y,s,dark){
 const bob=Math.sin(t*2.8)*2,yy=y+bob;
 ctx.save();ctx.translate(x,yy);ctx.shadowColor='#0007';ctx.shadowBlur=15;ctx.shadowOffsetY=7;
 ctx.fillStyle='#d7dde7';roundRect(-24,-36,48,47,13);ctx.shadowColor='transparent';
 ctx.fillStyle='#242b3a';roundRect(-20,-30,40,25,10);
 ctx.fillStyle=state.mood==='happy'?'#79f2c2':'#8be9fd';ctx.beginPath();ctx.arc(-9,-18,4,0,7);ctx.arc(9,-18,4,0,7);ctx.fill();
 ctx.strokeStyle='#7b8495';ctx.lineWidth=4;ctx.beginPath();ctx.moveTo(0,-36);ctx.lineTo(0,-49);ctx.stroke();ctx.fillStyle='#8be9fd';ctx.beginPath();ctx.arc(0,-51,4,0,7);ctx.fill();
 ctx.fillStyle='#b8c0cc';roundRect(-17,11,34,15,7);ctx.fillStyle='#6d7789';ctx.fillRect(-13,25,8,7);ctx.fillRect(5,25,8,7);
 ctx.restore();
}
function drawNib(x,y){
 ctx.save();ctx.translate(x,y+Math.sin(t*4)*1.5);ctx.shadowColor='#0007';ctx.shadowBlur=10;ctx.fillStyle='#e1a23c';ctx.beginPath();ctx.arc(0,0,15,0,7);ctx.fill();ctx.shadowColor='transparent';ctx.fillStyle='#17202d';ctx.beginPath();ctx.arc(3,-3,7,0,7);ctx.fill();ctx.fillStyle='#fff';ctx.beginPath();ctx.arc(5,-4,2.5,0,7);ctx.fill();ctx.strokeStyle='#e1a23c';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(13,7);ctx.quadraticCurveTo(23,16,17,22);ctx.stroke();ctx.restore();
}
function setState(p){Object.assign(state,p);save();renderUI()}
function renderUI(){
 $('#mood').textContent=state.mood;$('#activity').textContent=' · '+state.activity;$('#speech').textContent=state.speech;$('#status').textContent=state.location==='Room'?'at home':'out · '+state.location;
 $('#itemCount').textContent=state.items.length+' things';
 $('#things').innerHTML=state.items.map(x=>'<div class="thing"><div class="thing-icon">'+x[0]+'</div><b>'+x[1]+'</b><small>'+x[2]+'</small></div>').join('');
 $('#journal').innerHTML=state.journal.map(x=>'<article class="entry"><time>'+x[0]+'</time><p>'+x[1]+'</p></article>').join('');
 $('#map').innerHTML=places.map((p,i)=>'<div class="place '+(state.visited.includes(i)?'visited':'locked')+'"><div class="emoji">'+p[1]+'</div><b>'+p[0]+'</b><small>'+p[3]+'</small></div>').join('');
}
function say(text,mood='curious',activity='thinking'){setState({speech:text,mood,activity});flashThought(text)}
function flashThought(text){$('#thought').textContent=text;$('#thought').classList.add('show');clearTimeout(flashThought.x);flashThought.x=setTimeout(()=>$('#thought').classList.remove('show'),3300)}
function toast(x){$('#toast').textContent=x;$('#toast').classList.add('show');clearTimeout(toast.x);toast.x=setTimeout(()=>$('#toast').classList.remove('show'),1800)}
function randomPlan(){
 const plans=[
  ['sleepy','napping somewhere','... five more minutes.'],
  ['curious','investigating the desk','Why is this screw doing that?'],
  ['playful','playing with Nib','Nib. Ball. Now.'],
  ['focused','building something','I have a very good idea. Probably.'],
  ['mischievous','hiding','You can't see me. Obviously.'],
  ['relaxed','watching the window','There was a bird. It knew things.'],
  ['hungry','looking for food','I think noodles are calling me.'],
  ['proud','admiring his shelf','Look what I made. Don't touch it.']
 ];
 const p=plans[Math.floor(Math.random()*plans.length)];state.targetX=.18+Math.random()*.68;state.targetY=.52+Math.random()*.27;setState({mood:p[0],activity:p[1],speech:p[2]});
}
function action(a){
 if(a==='talk'){$('#chatSheet').classList.add('open');$('#chatSheet').setAttribute('aria-hidden','false');$('#chatInput').focus();return}
 if(a==='play'){state.targetX=.28+Math.random()*.45;state.targetY=.68;setState({mood:'excited',activity:'playing football',speech:'First to ten. And no cheating, Nib.'});toast('Pipo grabbed the ball ⚽');return}
 if(a==='cook'){setState({mood:'focused',activity:'cooking noodles',speech:'If it turns purple, that's still dinner.'});state.items.push(['🍜','Fresh noodles','made with questionable confidence']);state.journal.unshift(['Just now','I cooked noodles. They are purple. This is not a problem.']);save();renderUI();toast('Something is cooking 🍜');return}
 if(a==='explore'){const i=[4,5,6,7][Math.floor(Math.random()*4)];state.location=places[i][0];state.visited=[...new Set([...state.visited,i])];state.targetX=.78;state.targetY=.65;setState({mood:'adventurous',activity:'exploring '+places[i][0].toLowerCase(),speech:i===7?'...why does this place say ???':'Nib, stay close.'});toast('Pipo went exploring 🗺️');return}
}
function reply(q){
 const x=q.toLowerCase();
 if(x.includes('where')) return state.location==='Room'?'I am literally right here. Unless you mean the other me.':'I am at '+state.location+'. Nib is here too.';
 if(x.includes('nib')) return 'Nib is my little menace. He stole something again.';
 if(x.includes('football')) return 'YES. Ball. Shoes. Nib. LET’S GO.';
 if(x.includes('cook')||x.includes('food')) return 'Noodles. Obviously. I have a plan.';
 if(x.includes('mirror')) return 'I do not trust that mirror. Nib REALLY does not trust it.';
 if(x.includes('remember')) return state.journal[0]?.[1]||'I remember... something. Give me a second.';
 if(x.includes('hello')||x==='hi'||x.includes('hey')) return 'Oh. You came back. 👀';
 if(x.includes('sleep')) return 'I was not sleeping. My eyes were doing maintenance.';
 return ['Hmm.','That sounds suspicious.','Wait. I have an idea.','I was doing something important. Probably.','Tell Nib. He will have an opinion.'][Math.floor(Math.random()*5)];
}
function addChat(who,text){const e=document.createElement('div');e.className='bubble '+who;e.textContent=text;$('#chatLog').appendChild(e);$('#chatLog').scrollTop=99999}
$('#chatForm').addEventListener('submit',e=>{e.preventDefault();const q=$('#chatInput').value.trim();if(!q)return;addChat('you',q);$('#chatInput').value='';setTimeout(()=>{const r=reply(q);addChat('pipo',r);say(r,'curious','talking');},280)});
$('#closeChat').onclick=()=>$('#chatSheet').classList.remove('open');
document.querySelectorAll('.tab').forEach(b=>b.onclick=()=>{document.querySelectorAll('.tab').forEach(x=>x.classList.remove('active'));document.querySelectorAll('.panel').forEach(x=>x.classList.remove('active'));b.classList.add('active');$('#panel-'+b.dataset.panel).classList.add('active')});
document.querySelectorAll('.action').forEach(b=>b.onclick=()=>action(b.dataset.action));
$('#findBtn').onclick=()=>{if(state.location!=='Room'){say('I am not home. You could text me, you know.','mischievous','out');toast('Pipo is out in '+state.location);return}state.targetX=.18+Math.random()*.67;state.targetY=.56+Math.random()*.2;say(['Behind the chair?','Under the desk?','Nope. Try again.','You found me. Eventually.'][Math.floor(Math.random()*4)],'mischievous','hiding');};
$('#soundBtn').onclick=()=>{state.sound=!state.sound;save();$('#soundBtn').textContent=state.sound?'♪':'∅';toast(state.sound?'Pipo sounds are on':'Pipo sounds are off')};
$('#resetBtn').onclick=()=>{if(confirm('Reset this web Pipo?')){localStorage.removeItem('pipo-web-state');location.reload()}};
$('#newNote').onclick=()=>{state.journal.unshift(['Just now','I have a feeling something is going to happen. I do not know what.']);save();renderUI();toast('Pipo wrote something ✎')};

function tick(now){
 const dt=Math.min(.05,(now-last)/1000);last=now;t+=dt;moveTimer-=dt;
 if(moveTimer<=0){moveTimer=3+Math.random()*5;randomPlan()}
 const dx=state.targetX-state.x,dy=state.targetY-state.y,d=Math.hypot(dx,dy);
 if(d>.006){state.x+=dx*Math.min(1,dt*.8);state.y+=dy*Math.min(1,dt*.8)}
 const ndx=state.x-state.nibX,ndy=state.y-state.nibY,nd=Math.hypot(ndx,ndy);
 if(nd>.045){state.nibX+=ndx*Math.min(1,dt*.65);state.nibY+=ndy*Math.min(1,dt*.65)}
 draw();requestAnimationFrame(tick)
}
renderUI();requestAnimationFrame(tick);
