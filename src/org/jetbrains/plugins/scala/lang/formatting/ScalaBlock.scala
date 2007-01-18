package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting._;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.psi.ScalaFile;

import java.util.List;
import java.util.ArrayList;

class ScalaBlock(private val myParentBlock : ScalaBlock,
                 private val myNode : ASTNode,
                 private val myAlignment : Alignment,
                 private val myIndent: Indent,
                 private val myWrap : Wrap,
                 private val mySettings : CodeStyleSettings)
  extends Object with ScalaTokenTypes with Block {

  private var mySubBlocks : List[Block] = null

  def getNode = myNode

  def getSettings = mySettings

  def getTextRange = myNode.getTextRange

  def getIndent = myIndent

  def getWrap = myWrap

  def getAlignment = myAlignment

  def isLeaf = {
    myNode.getFirstChildNode() == null
  }

  def getChildAttributes(newChildIndex: Int) = {
    val parent = getNode.getPsi
    if (!parent.isInstanceOf[ScalaFile]) {
      new ChildAttributes(Indent.getNormalIndent(), null)
    }
    new ChildAttributes(Indent.getNoneIndent(), null)
  }

  def getSpacing(child1: Block, child2: Block) = {
    null
  }

  private def getFuckingBlocks = {
    var children = myNode.getChildren(null)
    var subBlocks = new ArrayList[Block]
    for (val child <- children) {
      if (isCorrectBlock(child)) {
        subBlocks.add(new ScalaBlock(this, child, myAlignment, myIndent, myWrap, mySettings))
      }
    }
    subBlocks
  }

  def getSubBlocks : List[Block] = {
    if (mySubBlocks == null) {
      mySubBlocks = getFuckingBlocks
    }
    mySubBlocks
  }

  def isIncomplete = {
    isIncomplete(myNode)
  }

  def isIncomplete (node: ASTNode) : Boolean = {
    var lastChild = node.getLastChildNode();
    while (lastChild != null &&
            (lastChild.getPsi.isInstanceOf[PsiWhiteSpace] || lastChild.getPsi.isInstanceOf[PsiComment])) {
        lastChild = lastChild.getTreePrev();
    }
    if (lastChild == null){
        return false;
    }
    if (lastChild.getPsi.isInstanceOf[PsiErrorElement]) {
        return true;
    }
    return isIncomplete(lastChild);
  }


  def isCorrectBlock(node:ASTNode) = {
    node.getText().trim().length()>0
  }

}