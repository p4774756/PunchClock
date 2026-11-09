import {lazy,Suspense} from 'react';
import {createRoot} from 'react-dom/client';
import '../app/globals.css';

const Home=lazy(()=>import('../app/page'));
createRoot(document.getElementById('root')!).render(
 <Suspense fallback={<div className="loading-card" role="status" style={{position:'fixed',top:'45%',left:'50%',transform:'translate(-50%,-50%)'}}><strong>正在載入教學臺</strong><p>準備穴位資料與介面。</p></div>}>
  <Home/>
 </Suspense>
);
