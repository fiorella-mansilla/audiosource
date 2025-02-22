import React from "react";
import routes from "./routes";
import {
  Route,
  Routes,
} from "react-router-dom";
import "./assets/css/pe-icon-7.css";
import "./assets/scss/themes.scss";

const App = () => {
  return (
    <Routes>
      {routes.map((route, idx) => (
        <Route
          path={route.path}
          element={<route.component />} 
          key={idx}
        />
      ))}
    </Routes>
  );
};

export default App; 
