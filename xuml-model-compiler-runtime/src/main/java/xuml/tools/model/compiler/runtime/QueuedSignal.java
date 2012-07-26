package xuml.tools.model.compiler.runtime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "xuml_queued_signal")
public class QueuedSignal {

	public QueuedSignal() {
		// jpa requires no-arg constructor
	}

	public QueuedSignal(byte[] idContent, String entityClassName,
			String eventClassName, byte[] eventContent) {
		this.idContent = idContent;
		this.entityClassName = entityClassName;
		this.eventClassName = eventClassName;
		this.eventContent = eventContent;
	}

	// TODO add new fields, numFailures, timeFirstFailure, timeLastFailure

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "signal_id")
	public Long id;

	@Column(name = "entity_class_name", nullable = false)
	public String entityClassName;

	@Column(name = "event_class_name", nullable = false)
	public String eventClassName;

	@Column(name = "id_content", nullable = false)
	public byte[] idContent;

	@Column(name = "event_content", nullable = false)
	public byte[] eventContent;

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("QueuedSignal [id=");
		builder.append(id);
		builder.append(", entityClassName=");
		builder.append(entityClassName);
		builder.append(", eventClassName=");
		builder.append(eventClassName);
		builder.append("]");
		return builder.toString();
	}

}
