The Fabric Language and Compiler {#language}
================================

  * @subpage language-overview
  * @subpage compiling

@page language-overview Language overview

The Fabric programming language is an extension of the Jif programming language
@cite jif-popl1999, which is in turn a version of Java extended with security labels
that govern the confidentiality and integrity of information used in the
program, and ensure that information flows in the programs respect those
security policies.  Therefore, a good place to start is with the [Jif
manual](http://www.cs.cornell.edu/jif/doc/jif-3.3.0/manual.html).

Fabric extends Jif with several additional features:
  - using and creating persistent objects on remote stores
  - nested transactions
  - remote method calls
  - access labels
  - provider labels
  - codebases

These features are summarized below, but more information can be found in two
papers about Fabric @cite fabric2009, @cite mobile-fabric-2012.

Persistent objects
------------------
  Fabric objects are, in general, persistent. Further, they may be stored
  persistently at a remote node (a storage node, or store). Applications that
  need persistent storage do not need a database to back them; they can record
  information directly in objects. Fabric supports _orthogonal persistence_:
  programs use objects in the same way regardless of whether they are
  persistent or not.

  Remote persistent objects are created by specifying a store to store them.
  For example:
~~~
      Store s = FabricWorker.getWorker().getStore("storename");
      Object o = new Object@s(args);
~~~
  If a store is not specified, objects are created at the same store
  as the object `this`. Each worker node also has a local,
  non-persistent store. A reference to this store can be obtained by
  calling `FabricWorker.getWorker().getLocalStore()`.

  Every object in Fabric has an _object label_ that specifies the
  security of the information it contains. The object label is declared
  by attaching it to a field or fields of the object. (If multiple
  fields have labels, the object label combines all of them.)

Nested transactions
-------------------
  Fabric computations are organized in _transactions_, which occur, as far as
  the programmer can tell, atomically and in isolation from the rest of the
  Fabric system.

  Transactions are specified with an atomic block, for example:
~~~
      atomic {
        o1.f();
        o2.g();
      }
~~~
  The semantics of the atomic block are that statements inside the
  atomic block are executed simultaneously and without
  interference from other concurrent transactions, even those taking
  place at other network nodes.

  Transactions may be nested freely. The results of a nested transaction are
  only visible to the outer transaction once it successfully commits.

Remote method calls
-------------------
  Unlike in most distributed object systems, computation in Fabric stays
  on the same network node unless the program explicitly transfers control
  to another node, using a remote method call.

  A remote method call is specified using the syntax `o.m@w(x)`. This is
  the same syntax as a Java method call, except for the annotation `@w`,
  which specifies the worker node at which to perform the method call.
~~~
  RemoteWorker w = FabricWorker.getWorker().getWorker("workername");
  o.m@w(args);
~~~

  Unlike many other distributed systems with remote calls, the objects
  used during the computation of the method `m` need not reside at the
  remote worker `w`. Also note that transactions can span multiple
  remote calls; these calls will be executed as a single transaction.

Access labels
-----------
  When an object is accessed during computation on a worker, but is not yet
  cached at the worker, the worker must fetch the object data from the node
  where it is stored.  Thus, the contacted node learns that an access to the
  object has occurred.  This side channel is called a _read channel_.
  
  Read channels are controlled by extending Jif with a second label on each
  object, called the _access label_. It is a confidentiality-only label
  that bounds what can be learned from the fact that the
  object has been accessed. The access label ensures that the object is
  stored on a node that is trusted to learn about all the accesses to
  it, and it prevents the object from being accessed from a
  context that is too confidential.

  The access label of an object is declared as part of the label of its fields.
  Given object label `{u}` and access label `{a}`, a label annotation `{u} @ {a}`
  means that the field, and by extension the object, has the corresponding
  labels. 

  For example, the following code declares an object containing public
  information (in field `data`) that can be accessed without leaking
  information, according to any principal that trusts node `n` to enforce its
  confidentiality:

~~~
  class Public {
      int {} @ {⊤→n} data;
  }
~~~
  In this example, the object label is `{}` (public and untrusted), and the
  access label is `{⊤→n}` (readable by principal `n`).

  If the access label is omitted from a field, its access label defaults to the
  label `{this.store→}`. For any object `o`, the pseudo-field `o.store`
  represents the node on which `o` is stored.

Provider labels
---------------
  Remote method calls make it possible to invoke a method on a remote node even
  when that node has not previously seen the class of the object receiving the
  call, or its code. To make this possible, Fabric code is stored in class
  objects, which are also persistent objects in Fabric. We refer to the act of
  adding a class object to Fabric as _publishing_ that class.
  
  All code has an information-flow label called the _provider label_,
  which bounds who can have influenced the code. In fact, this label is
  precisely the object label of the class object.

  Inside Fabric code, the provider label can be named explicitly as `provider`.
  Before loading Fabric code, a Fabric node checks the information flows within
  the code, using the provider label to implicitly keep track of the influence
  that the code publisher has on computations performed by the code.

Codebases
---------

  Unlike Java classes, Fabric class objects are accompanied by linkage
  specifications called _codebases_. There is no global mapping in Fabric from
  class names to class objects. Instead, each code publisher can choose their
  own mapping. Fabric helps to make sure that published code uses these
  namespaces consistently. Thus, codebases support _decentralized namespaces_;
  a class's own codebase defines the resolution of its dependencies.  Linkage
  of a component's dependencies is fixed at publication, so nodes that download
  and compile mobile code independently can securely interact with each other.

  Codebases are normally not visible in Fabric programs. However, to support
  evolution of running Fabric systems, it may be necessary to use two classes
  with the same fully qualified Java name within the same program. This is
  supported by the use of _explicit codebases_.
  
  For example, to specify that the name `pkg.A` should be resolved through
  a different codebase than the default one being used in the current 
  code, we might declare the existence of a separate codebase `cb1`:

~~~
package pkg;
codebase cb1;
class B extends C {
  void m(cb1.pkg.A a) {
    …
  }
}
~~~
   The fully qualified name `pkg.A` is resolved to a class object through
   a different class name than the current class, `pkg.B`.  The binding between
   the name `cb1` and the actual Fabric codebase object is done at the time of
   publication.

@page compiling Compiling and publishing Fabric programs

Compiling
---------
To compile a Fabric program `MyClass.fab`, run the Fabric compiler, `fabc`:
~~~
  $ fabc MyClass.fab
~~~
The `fabc` compiler has many options similar to the `javac` compiler,
including the `-d` option to output classes in a different directory,
the `-classpath` option to specify the classpath, and the `-sourcepath`
option to specify the source path. For a complete list of options, run:
~~~
  $ fabc -help
~~~


Publishing
----------
The Fabric compiler can publish code to a Fabric store, making the code
available for download and use by Fabric workers. Publishing code
requires a running store and a configured worker. (See @ref running.) To
publish, the Fabric compiler needs a few additional parameters:

  * the name of the store that will host the published code,
  * the name of the worker to use for publishing, and
  * a file to which to write codebase information.

The following command will use the worker `MyWorker` to publish
`MyClass.fab` to the store `MyStore`, outputting the URL of the
resulting codebase to the file `codebase.url`:
~~~
  $ fabc -deststore MyStore -worker MyWorker -publish-only \
      -codebase-output-file codebase.url MyClass.fab
~~~

The Fabric compiler can also compile against published code by
specifying the codebase file on the classpath:
~~~
  $ fabc -worker MyWorker -classpath @codebase.url MyClass2.fab
~~~

Code dependent on published code can similarly be published:
~~~
  $ fabc -deststore MyStore -worker MyWorker -publish-only \
      -codebase-output-file codebase2.url \
      -classpath @codebase.url MyClass2.fab
~~~
