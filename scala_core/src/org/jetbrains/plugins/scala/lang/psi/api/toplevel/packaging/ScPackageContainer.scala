package org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging

import typedef.ScTypeDefinition

/**
 * @author ilyas
 */

trait ScPackageContainer extends ScalaPsiElement {

  def prefix : String
  def ownNamePart : String

  def fqn = concat(prefix, ownNamePart)

  protected def concat(prefix : String, suffix : String) = if (prefix.length > 0) prefix + "." + suffix else suffix 

  def packagings: Seq[ScPackaging]

  def typeDefs: Seq[ScTypeDefinition]

}