import React from "react";

const Login = () => {
  const handleLogin = () => {
    window.location.href = "http://localhost:8080/oauth2/authorization/google";
  };

  return (
    <div className="flex flex-col items-center mt-32">
      <h2 className="text-2xl font-semibold mb-8">Login to Job Tracker</h2>
      <button
        onClick={handleLogin}
        className="px-8 py-3 text-lg rounded-xl bg-blue-600 text-white font-semibold shadow hover:bg-blue-700 transition-colors duration-150"
      >
        Login with Google
      </button>
    </div>
  );
};

export default Login;
