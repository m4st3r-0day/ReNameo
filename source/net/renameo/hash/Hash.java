
package net.renameo.hash;


public interface Hash {

	public void update(byte[] bytes, int off, int len);


	public String digest();

}
