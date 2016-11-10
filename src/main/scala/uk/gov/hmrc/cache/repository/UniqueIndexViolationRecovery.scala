package uk.gov.hmrc.cache.repository

import scala.concurrent.Future
import reactivemongo.core.errors.DatabaseException

import scala.concurrent.ExecutionContext.Implicits.global

trait UniqueIndexViolationRecovery {
  
  def recoverFromViolation[A](result: Future[A], fallback: => Future[A]) =
    result recoverWith { 
      case result: DatabaseException if result.code == Some(11000) => fallback
    }
    
}
