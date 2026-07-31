package de.zft2.gbanking.db.dao;

import java.io.Serializable;

public class BookingNoteDetails implements Serializable {

	private static final long serialVersionUID = 1L;

	private String note;
	private boolean reviewRequired;

	public BookingNoteDetails() {
	}

	public BookingNoteDetails(BookingNoteDetails detailsToCopy) {
		this.note = detailsToCopy.note;
		this.reviewRequired = detailsToCopy.reviewRequired;
	}

	public boolean isEmpty() {
		return (note == null || note.isBlank()) && !reviewRequired;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public boolean isReviewRequired() {
		return reviewRequired;
	}

	public void setReviewRequired(boolean reviewRequired) {
		this.reviewRequired = reviewRequired;
	}
}
