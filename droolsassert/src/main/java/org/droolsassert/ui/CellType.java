package org.droolsassert.ui;

public enum CellType {
	InsertedFact("#c8edc2", "#42b52f"),
	UpdatedFact("#c8edc2", "#42b52f"),
	DeletedFact("#ebebeb", "#9e9e9e"),
	Rule("#c7e7ff", "#4eb3fc"),
	Statistic("#ffffff", "#ffffff");

	String background;
	String borderColor;

	private CellType(String background, String borderColor) {
		this.background = background;
		this.borderColor = borderColor;
	}
}