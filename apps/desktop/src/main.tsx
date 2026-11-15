import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { Unreachable } from "./modules/dashboard/Unreachable";
import "./main.css";

// The dashboard's window shows this page when Nouride does not answer, in place of the WebView's own error page.
const page = location.hash.startsWith("#/unreachable") ? <Unreachable /> : <App />;

createRoot(document.getElementById("root") as HTMLElement).render(<StrictMode>{page}</StrictMode>);
