import { proxyFetch } from "@/lib/proxyfetch";

export async function GET(req: Request) {
    try {
        return await proxyFetch("/metrics/agents/live", {
            method: "GET",
            cache: "no-store",
        })
    } catch (error) {
        console.error("Error fetching agents metrics:", error);
        return new Response(JSON.stringify({ error: "Failed to fetch agents metrics" }), { status: 500 });
    }
}