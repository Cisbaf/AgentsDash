import { proxyFetch } from "@/lib/proxyfetch";

export async function GET(req: Request, { params }: { params: Promise<{ idAgents: string }> }) {
    try {
        const { idAgents } = await params;

        return await proxyFetch(`/agents/pauses/${idAgents}`, {
            method: "GET",
            cache: "no-store",
        })
    } catch (error) {
        console.error("Error fetching agents metrics:", error);
        return new Response(JSON.stringify({ error: "Failed to fetch agents metrics" }), { status: 500 });
    }
}