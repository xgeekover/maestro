import React from 'react'
import ReactDOM from 'react-dom/client'
import './monaco/setup' // Monaco 워커/로더 구성 + Java 보조 등록 (Editor 마운트 전)
import './styles.css'
import App from './App'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
