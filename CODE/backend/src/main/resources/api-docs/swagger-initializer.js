// SW-01,02,03,05,06: same-origin contract; no stored tokens or automatic auth requests.
window.addEventListener("load", () => {
  window.ui = SwaggerUIBundle({
    url: new URL("../openapi.yaml", window.location.href).href,
    dom_id: "#swagger-ui",
    layout: "BaseLayout",
    presets: [SwaggerUIBundle.presets.apis],
    filter: true,
    deepLinking: true,
    docExpansion: "list",
    displayRequestDuration: true,
    supportedSubmitMethods: ["get", "post", "delete"],
    tryItOutEnabled: false,
    persistAuthorization: false,
    withCredentials: false,
    queryConfigEnabled: false,
    validatorUrl: null
  });
});
