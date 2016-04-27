package edu.uci.ics.textdb.common.field;

import edu.uci.ics.textdb.api.common.IField;

/**
 * Created by chenli on 3/31/16. 
 * A field that is indexed and tokenized, without
 * term vectors. For example this would be used on a 'body' field, that contains
 * the bulk of a document's text.
 */
public class TextField implements IField {

	private final String value;

	public TextField(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		TextField that = (TextField) o;

		return value != null ? value.equals(that.value) : that.value == null;

	}

	@Override
	public int hashCode() {
		return value != null ? value.hashCode() : 0;
	}

	@Override
	public String toString() {
		return "TextField [value=" + value + "]";
	}

}
