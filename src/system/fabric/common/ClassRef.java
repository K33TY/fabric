package fabric.common;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Arrays;

import fabric.common.exceptions.InternalError;
import fabric.lang.Codebase;
import fabric.lang.FClass;
import fabric.lang.FClass._Proxy;
import fabric.lang.Object;
import fabric.lang.Object._Impl;
import fabric.worker.Store;
import fabric.worker.Worker;

/**
 * Encapsulates a reference to a class object.
 */
public abstract class ClassRef implements FastSerializable {

  /**
   * Gives a mapping between ClassRef types and ordinals. Used for efficient
   * encoding and decoding of the type of a ClassRef during serialization and
   * deserialization.
   */
  static enum ClassRefType {
    PLATFORM {
      @Override
      /**
       * @param pos
       *          the starting position of a serialized representation of a
       *          PlatformClassRef object.
       */
      ClassRef deserialize(byte[] data, int pos) {
        return new PlatformClassRef(data, pos);
      }

      @Override
      String className(byte[] data, int pos) {
        return PlatformClassRef.className(data, pos);
      }

      @Override
      void copySerialization(DataInput in, DataOutput out, byte[] buf)
          throws IOException {
        PlatformClassRef.copySerialization(in, out, buf);
      }

      /**
       * @param pos
       *          the starting position of a serialized representation of a
       *          PlatformClassRef object.
       */
      @Override
      int lengthAt(byte[] data, int pos) {
        return PlatformClassRef.lengthAt(data, pos);
      }

      @Override
      boolean isSurrogate(byte[] data, int pos) {
        return PlatformClassRef.isSurrogate(data, pos);
      }
    },
    FABRIC {
      /**
       * @param pos
       *          the starting position of a serialized representation of a
       *          FabricClassRef object.
       */
      @Override
      ClassRef deserialize(byte[] data, int pos) {
        return new FabricClassRef(data, pos);
      }

      @Override
      String className(byte[] data, int pos) {
        return "fab://" + FabricClassRef.storeName(data, pos) + "/"
            + FabricClassRef.onum(data, pos);
      }

      @Override
      void copySerialization(DataInput in, DataOutput out, byte[] buf)
          throws IOException {
        FabricClassRef.copySerialization(in, out, buf);
      }

      /**
       * @param pos
       *          the starting position of a serialized representation of a
       *          FabricClassRef object.
       */
      @Override
      int lengthAt(byte[] data, int pos) {
        return FabricClassRef.lengthAt(data, pos);
      }

      @Override
      boolean isSurrogate(byte[] data, int pos) {
        return false;
      }
    };

    abstract ClassRef deserialize(byte[] data, int pos);

    abstract String className(byte[] data, int pos);

    abstract void copySerialization(DataInput in, DataOutput out, byte[] buf)
        throws IOException;

    abstract int lengthAt(byte[] data, int pos);

    abstract boolean isSurrogate(byte[] data, int pos);
  }

  /**
   * ClassRef for fabric.common.Surrogate.
   */
  public static final ClassRef SURROGATE =
      new PlatformClassRef(Surrogate.class);

  /**
   * The <code>ClassRefType</code> corresponding to this class.
   */
  private ClassRefType type;

  /**
   * Memoization of the class's hash. This is null if the hash has not yet been
   * computed.
   */
  private byte[] hash;

  private ClassRef(ClassRefType type) {
    this.type = type;
    this.hash = null;
  }

  /**
   * Factory method.
   * 
   * @param clazz
   *          the class being referenced. If it's a Fabric class, this must be
   *          the interface corresponding to the Fabric type, and not the _Proxy
   *          or _Impl classes.
   */
  public static ClassRef makeRef(Class<?> clazz) {
    if (NSUtil.isPlatformName(clazz.getName()))
      return new PlatformClassRef(clazz);

    @SuppressWarnings("unchecked")
    Class<? extends fabric.lang.Object> fabClass =
    (Class<? extends fabric.lang.Object>) clazz;
    return new FabricClassRef(fabClass);
  }

  /**
   * Computes and returns the class's hash.
   */
  abstract byte[] getHashImpl();

  byte[] getHash() {
    if (hash != null) return hash;
    return this.hash = getHashImpl();
  }

  /**
   * @return the mangled Java class name for this class. This should not be
   *         called outside of this class.
   */
  abstract String javaClassName();

  /**
   * @return the Java Class object for this class. For Fabric classes (i.e.,
   *         ones that extend fabric.lang.Object), this is the Class object for
   *         the interface corresponding to the Fabric type, and not the _Proxy
   *         or _Impl classes.
   */
  public abstract Class<?> toClass();

  /**
   * @return the _Impl class object for this (assumed to be) Fabric class.
   * @throws InternalError
   *           if no _Impl class is found (usually because this is either a Java
   *           class or a Fabric interface)
   */
  public Class<? extends _Impl> toImplClass() {
    @SuppressWarnings("unchecked")
    Class<? extends Object> outer = (Class<? extends Object>) toClass();

    if (outer.equals(Surrogate.class)) {
      // Special case for Surrogate: it itself is an _Impl class.
      @SuppressWarnings("unchecked")
      Class<? extends _Impl> implClass = (Class<? extends _Impl>) outer;
      return implClass;
    }

    Class<? extends _Impl> result = SysUtil.getImplClass(outer);
    if (result == null) {
      throw new InternalError("No _Impl class found in " + outer);
    }

    return result;
  }

  /**
   * @return the _Proxy class object for this (assumed to be) Fabric class.
   * @throws InternalError
   *           if no _Proxy class is found (usually because this is not a Fabric
   *           class)
   */
  public Class<? extends fabric.lang.Object._Proxy> toProxyClass() {
    Class<?> outer = toClass();
    for (Class<?> c : outer.getClasses()) {
      if (c.getSimpleName().equals("_Proxy")) {
        @SuppressWarnings("unchecked")
        Class<? extends _Proxy> proxyClass = (Class<? extends _Proxy>) c;
        return proxyClass;
      }
    }

    throw new InternalError("No _Proxy class found in " + outer);
  }

  protected final void checkHash(byte[] hash) {
    byte[] myHash = getHash();

    boolean badHash = hash.length != myHash.length;

    for (int i = 0; !badHash && i < hash.length; i++) {
      badHash |= hash[i] != myHash[i];
    }

    if (badHash) {
      try {
        URL path = SysUtil.locateClass(javaClassName());
        throw new InternalError(new InvalidClassException(javaClassName(),
            "A class of the same name was found, but its hash did not match "
                + "the hash given in a network message" + "\n" + "hash from: "
                + path));
      } catch (ClassNotFoundException e) {
        throw new InternalError(e);
      }
    }
  }

  /**
   * ClassRef for classes not stored in Fabric.
   */
  private static final class PlatformClassRef extends ClassRef {
    /**
     * The class being referenced. If it's a Fabric class, this is the interface
     * corresponding to the Fabric type, and not the _Proxy or _Impl classes.
     */
    private Class<?> clazz;

    /**
     * @param clazz
     *          the class being referenced. If it's a Fabric class, this must be
     *          the interface corresponding to the Fabric type, and not the
     *          _Proxy or _Impl classes.
     */
    private PlatformClassRef(Class<?> clazz) {
      super(ClassRefType.PLATFORM);
      this.clazz = clazz;
    }

    @Override
    public String javaClassName() {
      return clazz.getName();
    }

    @Override
    public final Class<?> toClass() {
      return clazz;
    }

    @Override
    byte[] getHashImpl() {
      try {
        return SysUtil.hashPlatformClass(clazz);
      } catch (IOException e) {
        throw new InternalError(e);
      }
    }

    @Override
    boolean isSurrogate() {
      return Surrogate.class.equals(clazz);
    }

    // ////////////////////////////////////////////////////////////////////////
    // Serialization cruft.

    /**
     * Serialized format:
     * <ul>
     * <li>short length of class name</li>
     * <li>byte[] class name</li>
     * <li>short class hash length</li>
     * <li>byte[] class hash</li>
     * </ul>
     */
    @Override
    protected void writeImpl(DataOutput out) throws IOException {
      byte[] className = this.clazz.getName().getBytes("UTF-8");
      byte[] hash = getHash();

      out.writeShort(className.length);
      out.write(className);
      out.writeShort(hash.length);
      out.write(hash);
    }

    /**
     * Deserializes a PlatformClassRef object from the given byte array.
     * 
     * @param pos
     *          the starting position of a serialized representation of a
     *          PlatformClassRef object.
     */
    private PlatformClassRef(byte[] data, int pos) {
      super(ClassRefType.PLATFORM);
      try {
        this.clazz =
            Worker.getWorker().getClassLoader().loadClass(className(data, pos));
      } catch (ClassNotFoundException e) {
        throw new InternalError(e);
      }
      checkHash(classHash(data, pos));
    }

    /**
     * Copies a serialized PlatformClassRef from the given DataInput to the
     * given DataOutput.
     */
    static void copySerialization(DataInput in, DataOutput out, byte[] buf)
        throws IOException {
      int classNameLength = in.readShort();
      out.writeShort(classNameLength);
      SerializationUtil.copyBytes(in, out, classNameLength, buf);

      int classHashLength = in.readShort();
      out.writeShort(classHashLength);
      SerializationUtil.copyBytes(in, out, classHashLength, buf);
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          PlatformClassRef object.
     * @return the length of the serialized representation of a PlatformClassRef
     *         object starting at the given position in the given byte array.
     */
    static int lengthAt(byte[] data, int pos) {
      return classHashLengthPos(data, pos) + 2 + classHashLength(data, pos)
          - pos;
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          PlatformClassRef object.
     * @return position of classname-length field in serialized representation
     *         of PlatformClassRef starting at given position in the given byte
     *         array.
     */
    private static int classNameLengthPos(byte[] data, int pos) {
      return pos;
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          PlatformClassRef object.
     * @return length of class name in serialized representation of
     *         PlatformClassRef starting at given position in the given byte
     *         array.
     */
    private static int classNameLength(byte[] data, int pos) {
      return SerializationUtil.unsignedShortAt(data,
          classNameLengthPos(data, pos));
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          PlatformClassRef object.
     * @return class name in serialized representation of PlatformClassRef
     *         starting at given position in the given byte array.
     */
    private static String className(byte[] data, int pos) {
      int nameLength = classNameLength(data, pos);
      int nameDataPos = classNameLengthPos(data, pos) + 2;
      byte[] nameUTF8 =
          Arrays.copyOfRange(data, nameDataPos, nameDataPos + nameLength);
      try {
        return new String(nameUTF8, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        throw new InternalError(e);
      }
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          PlatformClassRef object.
     * @return position of class-hash-length field in serialized representation
     *         of PlatformClassRef starting at given position in the given byte
     *         array.
     */
    private static int classHashLengthPos(byte[] data, int pos) {
      return classNameLengthPos(data, pos) + 2 + classNameLength(data, pos);
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          PlatformClassRef object.
     * @return length of class hash in serialized representation of
     *         PlatformClassRef starting at given position in the given byte
     *         array.
     */
    private static int classHashLength(byte[] data, int pos) {
      return SerializationUtil.unsignedShortAt(data,
          classHashLengthPos(data, pos));
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          PlatformClassRef object.
     * @return the class hash in serialized representation of PlatformClassRef
     *         starting at given position in the given byte array.
     */
    private static byte[] classHash(byte[] data, int pos) {
      int classHashPos = classHashLengthPos(data, pos) + 2;
      return Arrays.copyOfRange(data, classHashPos, classHashPos
          + classHashLength(data, pos));
    }

    static boolean isSurrogate(byte[] data, int pos) {
      return className(data, pos).equals(Surrogate.class.getName());
    }
  }

  /**
   * ClassRef for classes stored in Fabric.
   */
  public static final class FabricClassRef extends ClassRef {

    /**
     * The OID of the class object, encapsulated as a proxy for the class's
     * FClass object.
     * <p>
     * Either <code>clazz</code> or (<code>codebase</code> and
     * <code>className</code>) must be non-null. If both are non-null, they are
     * assumed to refer to the same class object.
     * </p>
     */
    private FClass._Proxy fClass;

    /**
     * The OID of the class's codebase, encapsulated as a proxy for the
     * codebase's Codebase object. This is null for system classes.
     * <p>
     * Either <code>clazz</code> or (<code>codebase</code> and
     * <code>className</code>) must be non-null. If both are non-null, they are
     * assumed to refer to the same class object.
     * </p>
     */
    private Codebase._Proxy codebase;

    /**
     * The Fabric name of the class, relative to <code>codebase</code>.
     * <p>
     * Either <code>clazz</code> or (<code>codebase</code> and
     * <code>className</code>) must be non-null. If both are non-null, they are
     * assumed to refer to the same class object.
     * </p>
     */
    private String className;

    /**
     * @param clazz
     *          the class being referenced. This must be the interface
     *          corresponding to the Fabric type, and not the _Proxy or _Impl
     *          classes.
     */
    public FabricClassRef(Class<? extends fabric.lang.Object> clazz) {
      super(ClassRefType.FABRIC);
      try {
        this.fClass = (FClass._Proxy) NSUtil.toProxy(clazz.getName());
      } catch (final ClassNotFoundException e) {
        throw new InternalError("failed to resolve existing class", e);
      }
      this.codebase = null;
      this.className = null;
    }

    private FabricClassRef(Codebase._Proxy codebase, String className) {
      super(ClassRefType.FABRIC);
      this.fClass = null;
      this.codebase = codebase;
      this.className = className;
    }

    private FabricClassRef(FClass._Proxy fClass) {
      super(ClassRefType.FABRIC);
      this.fClass = fClass;
      this.codebase = null;
      this.className = null;
    }

    @Override
    public String javaClassName() {
      return NSUtil.javaClassName(getFClass());
    }

    @Override
    byte[] getHashImpl() {
      return SysUtil.hashFClass(this);
    }

    @Override
    public final Class<? extends fabric.lang.Object> toClass() {
      try {
        return
            (Class<? extends Object>) Worker.getWorker().getClassLoader()
                .loadClass(javaClassName());
      } catch (ClassNotFoundException e) {
        throw new InternalError(e);
      }
    }

    private FClass._Proxy getFClass() {
      if (fClass != null) return fClass;
      return (_Proxy) codebase.resolveClassName(className);
    }

    @Override
    boolean isSurrogate() {
      return false;
    }

    // ////////////////////////////////////////////////////////////////////////
    // Serialization cruft.

    /**
     * Serialized format:
     * <ul>
     * <li>short length of class OID's store's name</li>
     * <li>byte[] class OID's store's name</li>
     * <li>long class OID's onum</li>
     * <li>short class hash length</li>
     * <li>byte[] class hash</li>
     * </ul>
     */
    @Override
    protected void writeImpl(DataOutput out) throws IOException {
      // Ensure the clazz field is populated and the class has been hashed.
      FClass._Proxy fClass = getFClass();
      byte[] hash = getHash();

      String storeName = fClass.$getStore().name();
      long onum = fClass.$getOnum();

      byte[] storeNameUTF = storeName.getBytes("UTF-8");

      out.writeShort(storeNameUTF.length);
      out.write(storeNameUTF);
      out.writeLong(onum);
      out.writeShort(hash.length);
      out.write(hash);
    }

    /**
     * Deserializes a FabricClassRef object from the given byte array.
     * 
     * @param pos
     *          the starting position of a serialized representation of a
     *          FabricClassRef object.
     */
    private FabricClassRef(byte[] data, int pos) {
      this(new FClass._Proxy(store(data, pos), onum(data, pos)));
      checkHash(classHash(data, pos));
    }

    /**
     * Deserializes a FabricClassRef object from the given DataInput.
     */
    public FabricClassRef(DataInput in) throws IOException {
      super(ClassRefType.FABRIC);

      byte[] storeNameData = new byte[in.readShort()];
      in.readFully(storeNameData);
      String storeName = new String(storeNameData, "UTF-8");
      Store store = Worker.getWorker().getStore(storeName);

      long onum = in.readLong();
      this.fClass = new FClass._Proxy(store, onum);
      this.codebase = null;
      this.className = null;

      byte[] hash = new byte[in.readShort()];
      in.readFully(hash);
      checkHash(hash);
    }

    /**
     * Copies a serialized FabricClassRef from the given DataInput to the given
     * DataOutput.
     */
    static void copySerialization(DataInput in, DataOutput out, byte[] buf)
        throws IOException {
      int storeNameLength = in.readShort();
      out.writeShort(storeNameLength);
      SerializationUtil.copyBytes(in, out, storeNameLength, buf);

      long onum = in.readLong();
      out.writeLong(onum);

      int classHashLength = in.readShort();
      out.writeShort(classHashLength);
      SerializationUtil.copyBytes(in, out, classHashLength, buf);
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          FabricClassRef object.
     * @return the length of the serialized representation of a FabricClassRef
     *         object starting at the given position in the given byte array.
     */
    static int lengthAt(byte[] data, int pos) {
      return classHashLengthPos(data, pos) + 2 + classHashLength(data, pos)
          - pos;
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          FabricClassRef object.
     * @return position of store-name-length field in serialized representation
     *         of FabricClassRef starting at given position in the given byte
     *         array.
     */
    private static int storeNameLengthPos(byte[] data, int pos) {
      return pos;
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          FabricClassRef object.
     * @return the length of store name in serialized representation of
     *         FabricClassRef starting at given position in the given byte
     *         array.
     */
    private static int storeNameLength(byte[] data, int pos) {
      int x =
          SerializationUtil
          .unsignedShortAt(data, storeNameLengthPos(data, pos));
      return x;
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          FabricClassRef object.
     * @return store name in serialized representation of FabricClassRef
     *         starting at given position in the given byte array.
     */
    private static String storeName(byte[] data, int pos) {
      int nameLength = storeNameLength(data, pos);
      int nameDataPos = storeNameLengthPos(data, pos) + 2;
      byte[] nameUTF8 =
          Arrays.copyOfRange(data, nameDataPos, nameDataPos + nameLength);
      try {
        return new String(nameUTF8, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        throw new InternalError(e);
      }
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          FabricClassRef object.
     * @return store named in serialized representation of FabricClassRef
     *         starting at given position in the given byte array.
     */
    private static Store store(byte[] data, int pos) {
      String name = storeName(data, pos);
      return Worker.getWorker().getStore(name);
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          FabricClassRef object.
     * @return position of onum field in serialized representation of
     *         FabricClassRef starting at given position in the given byte
     *         array.
     */
    private static int onumPos(byte[] data, int pos) {
      int x = storeNameLengthPos(data, pos) + 2 + storeNameLength(data, pos);
      return x;
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          FabricClassRef object.
     * @return onum in serialized representation of FabricClassRef starting at
     *         given position in the given byte array.
     */
    private static long onum(byte[] data, int pos) {
      return SerializationUtil.longAt(data, onumPos(data, pos));
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          FabricClassRef object.
     * @return position of class-hash-length field in serialized representation
     *         of FabricClassRef starting at given position in the given byte
     *         array.
     */
    private static int classHashLengthPos(byte[] data, int pos) {
      return onumPos(data, pos) + 8;
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          FabricClassRef object.
     * @return length of class hash in serialized representation of
     *         FabricClassRef starting at given position in the given byte
     *         array.
     */
    private static int classHashLength(byte[] data, int pos) {
      return SerializationUtil.unsignedShortAt(data,
          classHashLengthPos(data, pos));
    }

    /**
     * @param pos
     *          the starting position of a serialized representation of a
     *          FabricClassRef object.
     * @return the class hash in serialized representation of FabricClassRef
     *         starting at given position in the given byte array.
     */
    private static byte[] classHash(byte[] data, int pos) {
      int classHashPos = classHashLengthPos(data, pos) + 2;
      return Arrays.copyOfRange(data, classHashPos, classHashPos
          + classHashLength(data, pos));
    }
  }

  /**
   * @return true iff this ClassRef represents
   *         <code>fabric.common.Surrogate</code>.
   */
  abstract boolean isSurrogate();

  // //////////////////////////////////////////////////////////////////////////
  // Serialization cruft.

  /**
   * Serialized format:
   * <ul>
   * <li>byte ClassRefType</li>
   * <li>byte[] ClassRef-specific data, specified by <code>writeImpl</code></li>
   * </ul>
   */
  @Override
  public final void write(DataOutput out) throws IOException {
    out.writeByte(type.ordinal());
    writeImpl(out);
  }

  /**
   * Writes internal representation of this class ref to the given output.
   */
  protected abstract void writeImpl(DataOutput out) throws IOException;

  /**
   * Copies a deserialized ClassRef from the given DataInput to the given
   * DataOutput, using the given buffer.
   */
  static void copySerialization(DataInput in, DataOutput out, byte[] buf)
      throws IOException {
    int ord = in.readByte();
    out.writeByte(ord);
    ClassRefType type = ClassRefType.values()[ord];
    type.copySerialization(in, out, buf);
  }

  /**
   * Deserializes a ClassRef starting at the given position in the given byte
   * array.
   */
  public static ClassRef deserialize(byte[] data, int pos) {
    ClassRefType type = ClassRefType.values()[data[pos]];
    return type.deserialize(data, pos + 1);
  }

  /**
   * Gets the name of the class represented by the ClassRef starting at the
   * given position in the given byte array.
   */
  public static String getClassName(byte[] data, int pos) {
    ClassRefType type = ClassRefType.values()[data[pos]];
    return type.className(data, pos + 1);
  }

  /**
   * @return the length of the ClassRef data occurring at the given offset
   *         position in the given byte array.
   */
  static int lengthAt(byte[] data, int pos) {
    ClassRefType type = ClassRefType.values()[data[pos]];
    return 1 + type.lengthAt(data, pos + 1);
  }

  /**
   * Determines whether the ClassRef data occurring at the given offset position
   * in the give byte array represents the class
   * <code>fabric.common.Surrogate</code>.
   */
  static boolean isSurrogate(byte[] data, int pos) {
    ClassRefType type = ClassRefType.values()[data[pos]];
    return type.isSurrogate(data, pos + 1);
  }
}
