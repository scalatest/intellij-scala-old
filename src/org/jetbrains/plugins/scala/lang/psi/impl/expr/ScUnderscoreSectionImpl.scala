package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import types.result.{Success, TypeResult, Failure, TypingContext}
import types._
import api.base.patterns.ScBindingPattern
import api.statements.{ScVariable, ScValue, ScFunction}
import api.statements.params.ScClassParameter
import nonvalue.{ScTypePolymorphicType, ScMethodType}
import api.ScalaElementVisitor
import com.intellij.psi.{PsiElementVisitor, PsiElement}
import resolve.ScalaResolveResult

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

class ScUnderscoreSectionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScUnderscoreSection {
  override def toString: String = "UnderscoreSection"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    bindingExpr match {
      case Some(ref: ScReferenceExpression) =>
        def fun(): TypeResult[ScType] = {
          ref.getNonValueType(TypingContext.empty).map {
            case ScTypePolymorphicType(internalType, typeParameters) =>
              ScTypePolymorphicType(ScMethodType(internalType, Nil, isImplicit = false)(getProject, getResolveScope), typeParameters)
            case tp: ScType => ScMethodType(tp, Nil, isImplicit = false)(getProject, getResolveScope)
          }
        }
        ref.bind() match {
          case Some(ScalaResolveResult(f: ScFunction, _)) if f.paramClauses.clauses.length == 0 => fun()
          case Some(ScalaResolveResult(c: ScClassParameter, _)) if c.isVal | c.isVar => fun()
          case Some(ScalaResolveResult(b: ScBindingPattern, _)) =>
            b.nameContext match {
              case _: ScValue | _: ScVariable if b.isClassMember => fun()
              case _ => ref.getNonValueType(TypingContext.empty)
            }
            //the following code not works, because parameters shouldn't be computed with expected type,
            // we already know, which type they has
          /*case Some(ScalaResolveResult(f: ScFunction, subst)) =>
            val needType = expectedType().isEmpty
            val clauses = f.effectiveParameterClauses.filterNot(_.isImplicit).zipWithIndex
            val (forParams, forExprs) = clauses.map {
              case (clause, clauseIndex) =>
                val (forParams, forExprs) = clause.parameters.zipWithIndex.map {
                  case (parameter, index) =>
                    val name = "parameter" + index + "inClause" + clauseIndex
                    if (needType) {
                      val typeText = subst.subst(parameter.getType(TypingContext.empty).getOrAny).presentableText
                      (name + " : " + typeText, name)
                    }  else {
                      (name, name)
                    }
                }.unzip
                (forParams.mkString("(", ", ", ")"), forExprs.mkString("(", ", ", ")"))
            }.unzip
            val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(forParams.mkString("", " => ", " => ") +
              ref.getText + forExprs.mkString, getContext, this)
            newExpr.getType(TypingContext.empty)*/
          case _ => ref.getNonValueType(TypingContext.empty)
        }
      case Some(expr) => expr.getNonValueType(TypingContext.empty)
      case None => {
        getContext match {
          case typed: ScTypedStmt => {
            overExpr match {
              case Some(`typed`) => {
                typed.typeElement match {
                  case Some(te) => return te.getType(TypingContext.empty)
                  case _ => return Failure("Typed statement is not complete for underscore section", Some(this))
                }
              }
              case _ => return typed.getType(TypingContext.empty)
            }
          }
          case _ =>
        }
        overExpr match {
          case None => Failure("No type inferred", None)
          case Some(expr: ScExpression) => {
            val unders = ScUnderScoreSectionUtil.underscores(expr)
            var startOffset = if (expr.getTextRange != null) expr.getTextRange.getStartOffset else 0
            var e: PsiElement = this
            while (e != expr) {
              startOffset += e.getStartOffsetInParent
              e = e.getContext
            }
            val i = unders.indexWhere(_.getTextRange.getStartOffset == startOffset)
            if (i < 0) return Failure("Not found under", None)
            var result: Option[ScType] = null //strange logic to handle problems with detecting type
            var forEqualsParamLength: Boolean = false //this is for working completion
            for (tp <- expr.expectedTypes(fromUnderscore = false) if result != None) {

              def processFunctionType(tp: ScFunctionType) {
                import tp.params
                if (result != null) {
                  if (params.length == unders.length && !forEqualsParamLength) {
                    result = Some(params(i).removeAbstracts)
                    forEqualsParamLength = true
                  } else if (params.length == unders.length) result = None
                }
                else if (params.length > unders.length) result = Some(params(i).removeAbstracts)
                else {
                  result = Some(params(i).removeAbstracts)
                  forEqualsParamLength = true
                }
              }

              ScType.extractFunctionType(tp) match {
                case Some(ft@ScFunctionType(_, params)) if params.length >= unders.length => processFunctionType(ft)
                case _ =>
              }
            }
            if (result == null || result == None) {
              expectedType(fromUnderscore = false) match {
                case Some(tp: ScType) => result = Some(tp)
                case _ => result = None
              }
            }
            result match {
              case None => Failure("No type inferred", None)
              case Some(t) => Success(t, None)
            }
          }
        }
      }
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitUnderscoreExpression(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitUnderscoreExpression(this)
      case _ => super.accept(visitor)
    }
  }
}