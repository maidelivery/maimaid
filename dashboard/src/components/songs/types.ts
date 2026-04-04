export type Song = {
	songIdentifier: string;
	songId: number;
	title: string;
	artist: string;
	category?: string;
	sortOrder?: number;
	version?: string | null;
	releaseDate?: string | null;
	bpm?: number | null;
	isLocked?: boolean;
	isNew?: boolean;
	comment?: string | null;
	searchKeywords?: string | null;
	imageName?: string;
};

export type Sheet = {
	id: string;
	songIdentifier: string;
	songId?: number;
	chartType: string;
	difficulty: string;
	level: string;
	version?: string | null;
	levelValue?: number | null;
	internalLevel?: string | null;
	internalLevelValue?: number | null;
	noteDesigner?: string | null;
	tap?: number | null;
	hold?: number | null;
	slide?: number | null;
	touch?: number | null;
	breakCount?: number | null;
	total?: number | null;
	regionJp?: boolean;
	regionIntl?: boolean;
	regionUsa?: boolean;
	regionCn?: boolean;
	isSpecial?: boolean;
};

export type Alias = {
	id: string;
	songIdentifier: string;
	aliasText: string;
	source: string;
};

export type SongFilterSettings = {
	selectedCategories: Set<string>;
	selectedVersions: Set<string>;
	selectedDifficulties: Set<string>;
	selectedTypes: Set<string>;
	minLevel: number;
	maxLevel: number;
	showFavoritesOnly: boolean;
	hideDeletedSongs: boolean;
};

export type SongSortOption = "default" | "versionDate" | "difficulty";
