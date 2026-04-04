export type CandidateStatus = "approved" | "rejected" | "voting" | string;

export interface FilterState {
	search: string;
	status: string;
	sort: string;
}

export interface SongCatalogItem {
	songIdentifier: string;
	title: string;
	artist: string;
	version: string;
	coverUrl: string | null;
	searchText: string;
}

export interface AdminContext {
	user_id: string | null;
	email: string;
	is_admin: boolean;
}

export interface DashboardStats {
	total_count: number;
	voting_count: number;
	approved_count: number;
	rejected_count: number;
	closing_soon_count: number;
	expired_voting_count: number;
	today_submissions: number;
}

export interface CandidateRecord {
	candidate_id: string;
	song_identifier: string;
	alias_text: string;
	submitter_id: string;
	submitter_email?: string | null;
	status: CandidateStatus;
	vote_open_at: string | null;
	vote_close_at: string | null;
	approved_at: string | null;
	rejected_at: string | null;
	support_count: number;
	oppose_count: number;
	total_count: number;
	created_at: string;
	updated_at: string;
}

export interface ToastState {
	open: boolean;
	message: string;
	severity: "success" | "info" | "warning" | "error";
}
