import { proxyFetch } from "@/lib/proxyfetch";

export async function GET(req: Request, { params }: { params: Promise<{ agentId: string }> }) {
    try {
        const { agentId } = await params;

        return await proxyFetch(`/metrics/ligacao/${encodeURIComponent(agentId)}`, {
            method: "GET",
            cache: "no-store",
        })
    } catch (error) {
        console.error("Error fetching global metrics:", error);
        return new Response(JSON.stringify({ error: "Failed to fetch global metrics" }), { status: 500 });
    }
}