import { proxyFetch } from "@/lib/proxyfetch";

export async function GET(req: Request) {
    try {
        return await proxyFetch("/metrics/fila/live", {
            method: "GET",
            cache: "no-store",
        })
    } catch (error) {
        console.error("Error fetching fila metrics:", error);
        return new Response(JSON.stringify({ error: "Failed to fetch fila metrics" }), { status: 500 });
    }
}