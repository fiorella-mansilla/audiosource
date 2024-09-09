const validateForm = (file, separationType, userEmail, outputFormat) => {
    if (!file) {
        return 'Please select a file to upload.';
    }
    if (!separationType || !userEmail || !outputFormat) {
        return 'Please fill out all fields.';
    }
    return null;
}

export default validateForm;