"use client";

import { useEffect, useState } from "react";
import App from "@/App";
import "@/lib/i18n";
import { syncClientLanguagePreference } from "@/lib/i18n";
import { PublicCollectionPage } from "@/components/collections/PublicCollectionPage";
import { collectionSegmentFromPathname } from "@/lib/song-collection-codec";

export default function DashboardPage() {
	const [collectionSegment, setCollectionSegment] = useState<string | null>(null);
	const [routeResolved, setRouteResolved] = useState(false);

	useEffect(() => {
		syncClientLanguagePreference();
		const querySegment = new URLSearchParams(window.location.search).get("collection");
		setCollectionSegment(querySegment ?? collectionSegmentFromPathname(window.location.pathname));
		setRouteResolved(true);
	}, []);

	if (!routeResolved) {
		return null;
	}

	if (collectionSegment) {
		return <PublicCollectionPage segment={collectionSegment} />;
	}

	return <App />;
}
