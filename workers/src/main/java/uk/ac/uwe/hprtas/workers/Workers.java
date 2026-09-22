package uk.ac.uwe.hprtas.workers;

import uk.ac.uwe.hprtas.workers.workers.AppointmentAvailability;
import uk.ac.uwe.hprtas.workers.workers.CorrespondenceDispatch;
import uk.ac.uwe.hprtas.workers.workers.PaymentProcessing;
import uk.ac.uwe.hprtas.workers.workers.ReferralValidation;
import uk.ac.uwe.hprtas.workers.workers.RefundProcessing;
import uk.ac.uwe.hprtas.workers.workers.TreatmentAvailability;

import java.util.List;

/** Every external worker, in the order they are registered and reported. */
public final class Workers {

  private Workers() {}

  public static final List<WorkerModule> ALL =
      List.of(
          new ReferralValidation(),
          new AppointmentAvailability(),
          new TreatmentAvailability(),
          new PaymentProcessing(),
          new CorrespondenceDispatch(),
          new RefundProcessing());
}
