import { proxyFetch } from "@/lib/proxyfetch";

export async function GET(req: Request) {
    try {
        return await proxyFetch("/metrics/global/live", {
            method: "GET",
            cache: "no-store",
        })
    } catch (error) {
        console.error("Error fetching global metrics:", error);
        return new Response(JSON.stringify({ error: "Failed to fetch global metrics" }), { status: 500 });
    }
}