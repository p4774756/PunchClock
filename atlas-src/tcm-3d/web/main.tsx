import {lazy,Suspense} from 'react';
import {createRoot} from 'react-dom/client';
import '../app/globals.css';

const Home=lazy(()=>import('../app/page'));
createRoot(document.getElementById('root')!).render(
 <Suspense fallback={<div className="boot-screen" role="status"><div className="loading-card woodcut-loading"><strong>正在載入教學臺</strong><p>準備穴位資料與介面。</p></div></div>}>
  <Home/>
 </Suspense>
);
