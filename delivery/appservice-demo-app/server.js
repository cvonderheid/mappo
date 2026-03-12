const http = require("node:http");

function jsonResponse(statusCode, payload) {
  return {
    statusCode,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store"
    },
    body: JSON.stringify(payload, null, 2)
  };
}

function currentState() {
  return {
    app: "mappo-appservice-demo-app",
    projectId: "azure-appservice-ado-pipeline",
    targetId: process.env.MAPPO_TARGET_ID || "unknown",
    softwareVersion: process.env.APP_VERSION || "unknown",
    dataModelVersion: process.env.DATA_MODEL_VERSION || "unknown",
    deployedBy: process.env.DEPLOYED_BY || "pulumi",
    timestamp: new Date().toISOString()
  };
}

const server = http.createServer((request, response) => {
  let payload;
  if (request.url === "/health") {
    payload = jsonResponse(200, {
      status: "healthy",
      ...currentState()
    });
  } else if (request.url === "/version") {
    payload = jsonResponse(200, currentState());
  } else {
    payload = jsonResponse(200, {
      message: "MAPPO App Service demo target",
      ...currentState()
    });
  }

  response.writeHead(payload.statusCode, payload.headers);
  response.end(payload.body);
});

const port = Number.parseInt(process.env.PORT || "8080", 10);
server.listen(port);
