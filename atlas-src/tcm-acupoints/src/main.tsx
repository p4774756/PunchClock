import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AcupointApp } from "./components/AcupointApp";
import "./index.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AcupointApp />
  </StrictMode>,
);
