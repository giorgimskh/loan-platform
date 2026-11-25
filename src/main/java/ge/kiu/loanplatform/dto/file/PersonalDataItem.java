package ge.kiu.loanplatform.dto.file;

// One place in an application file that holds personal data, found by PersonalDataVisitor.
// source = which part of the file (e.g. "Customer", "Document 10 (ID Card)"),
// field  = what it is (e.g. "Personal number"), value = what is stored.
public record PersonalDataItem(
        String source,
        String field,
        String value
) {
}
