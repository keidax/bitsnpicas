package com.kreative.bitsnpicas.edit;

import java.util.*;

import com.kreative.bitsnpicas.Font;

public class FontCodePointList extends AbstractList<Integer> {
	private final Font<?> font;
	
	public FontCodePointList(Font<?> font) {
		this.font = font;
	}
	
	private List<Integer> codePoints() {
		List<Integer> arr = font.codePointList();
		Collections.sort(arr, new Comparator<Integer>() {
			@Override
			// Sort negative codepoints (unencoded glyphs) after positive ones and in reverse order.
			public int compare(Integer codepoint1, Integer codepoint2) {
				if (codepoint1 < 0 && codepoint2 >= 0) {
					return 1;
				} else if (codepoint2 < 0 && codepoint1 >= 0) {
					return -1;
				} else if (codepoint1 >= 0) {
					return codepoint1.compareTo(codepoint2);
				} else {
					// they must both be negative, flip the order
					return codepoint2.compareTo(codepoint1);
				}
			}
		});

		return arr;
	}
	
	@Override
	public boolean contains(Object e) {
		return codePoints().contains(e);
	}
	
	@Override
	public boolean containsAll(Collection<?> c) {
		return codePoints().containsAll(c);
	}
	
	@Override
	public Integer get(int i) {
		return codePoints().get(i);
	}
	
	@Override
	public int indexOf(Object e) {
		return codePoints().indexOf(e);
	}
	
	@Override
	public boolean isEmpty() {
		return font.isEmpty();
	}
	
	@Override
	public Iterator<Integer> iterator() {
		return codePoints().iterator();
	}
	
	@Override
	public int lastIndexOf(Object e) {
		return codePoints().lastIndexOf(e);
	}
	
	@Override
	public ListIterator<Integer> listIterator() {
		return codePoints().listIterator();
	}
	
	@Override
	public ListIterator<Integer> listIterator(int i) {
		return codePoints().listIterator(i);
	}
	
	@Override
	public int size() {
		return codePoints().size();
	}
	
	@Override
	public Object[] toArray() {
		return codePoints().toArray();
	}
	
	@Override
	public <T> T[] toArray(T[] a) {
		return codePoints().toArray(a);
	}
	
	@Override
	public String toString() {
		return "Characters in Font";
	}
}
