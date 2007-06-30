package tree;

public class Tree {
  private TreeNode root;
  private long nodeLocation;

  Tree(long nodeLocation) {
    this.root = null;
    this.nodeLocation = nodeLocation;
  }

  public void insert(int value) {
    root = insertHelper(root, value);
  }

  private TreeNode insertHelper(TreeNode cur, int value) {
    if (cur == null)
      return new TreeNode@nodeLocation(null, null, value);

    if (cur.value < value)
      cur.right = insertHelper(cur.right, value);
    else
      cur.left = insertHelper(cur.left, value);

    return cur;
  }

  public void insertIterative(int value) {
    TreeNode newNode = new TreeNode@nodeLocation(null, null, value);

    if (root == null) {
      root = newNode;
      return;
    }

    TreeNode cur = root;
    while (true) {
      if (cur.value < value) {
	if (cur.right == null) {
	  cur.right = newNode;
	  return;
	}
	cur = cur.right;
	continue;
      }

      if (cur.left == null) {
	cur.left = newNode;
	return;
      }
      cur = cur.left;
    }
  }

  public TreeNode lookup(int value) {
    TreeNode cur = root;
    while (cur != null) {
      if (cur.value == value) return cur;
      if (cur.value < value) cur = cur.left;
      else cur = cur.right;
    }

    return null;
  }
}

