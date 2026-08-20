const ALLOWED_REQUESTS = new Map<string, { method: string; url: string }>([
	["/diving-fish/discovery", { method: "GET", url: "https://auth.diving-fish.com/.well-known/openid-configuration" }],
	["/diving-fish/token", { method: "POST", url: "https://auth.diving-fish.com/oauth/token" }],
	["/diving-fish/userinfo", { method: "GET", url: "https://auth.diving-fish.com/oauth/userinfo" }],
	["/diving-fish/records", { method: "GET", url: "https://www.diving-fish.com/api/maimaidxprober/player/records" }],
	["/lxns/token", { method: "POST", url: "https://maimai.lxns.net/api/v0/oauth/token" }],
	["/lxns/player", { method: "GET", url: "https://maimai.lxns.net/api/v0/user/maimai/player" }],
	["/lxns/scores", { method: "GET", url: "https://maimai.lxns.net/api/v0/user/maimai/player/scores" }],
]);

type Env = {
	OAUTH_UPSTREAM_TOKEN: string;
};

export default {
	async fetch(request: Request, env: Env) {
		const url = new URL(request.url);
		const target = ALLOWED_REQUESTS.get(url.pathname);
		const token = request.headers.get("X-Maimaid-OAuth-Proxy-Token");
		if (!target || request.method !== target.method || !env.OAUTH_UPSTREAM_TOKEN || token !== env.OAUTH_UPSTREAM_TOKEN) {
			return Response.json({ code: "forbidden", message: "Upstream request is not allowed." }, { status: 403 });
		}
		const headers = new Headers();
		for (const name of ["Accept", "Authorization", "Content-Type"]) {
			const value = request.headers.get(name);
			if (value) {
				headers.set(name, value);
			}
		}
		return fetch(target.url, {
			method: request.method,
			headers,
			body: request.method === "GET" ? undefined : request.body,
		});
	},
};
